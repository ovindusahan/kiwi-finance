package nz.kiwifinance.bankfeed;

import java.time.Clock;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import nz.kiwifinance.account.Account;
import nz.kiwifinance.account.AccountService;
import nz.kiwifinance.account.ManagedBy;
import nz.kiwifinance.common.error.ApiException;
import nz.kiwifinance.common.error.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Provider-neutral management of bank connections and the accounts they expose.
 */
@Service
@Log4j2
@RequiredArgsConstructor
public class BankConnectionService {

    private final BankConnectionRepository connections;
    private final BankFeedAccountRepository feedAccounts;
    private final BankSyncRunRepository syncRuns;
    private final CredentialStore credentialStore;
    private final FeedAccountRefresher refresher;
    private final AccountService accounts;
    private final BankFeedClients clients;
    private final BankSyncService syncService;
    private final TransactionTemplate transaction;
    private final Clock clock;

    @Transactional(readOnly = true)
    public List<BankFeedResponses.Connection> list(UUID userId) {
        return connections.findCurrent(userId).stream().map(this::view).toList();
    }

    @Transactional(readOnly = true)
    public BankFeedResponses.Connection view(UUID userId, UUID connectionId) {
        return view(get(userId, connectionId));
    }

    @Transactional(readOnly = true)
    public BankConnection get(UUID userId, UUID connectionId) {
        return connections
                .findByIdAndUserId(connectionId, userId)
                .filter(c -> c.getStatus() != ConnectionStatus.DISCONNECTED)
                .orElseThrow(() -> ApiException.notFound("Bank connection"));
    }

    @Transactional(readOnly = true)
    public Optional<BankConnection> current(UUID userId, BankFeedProvider provider) {
        return connections.findCurrent(userId, provider);
    }

    @Transactional(readOnly = true)
    public List<BankFeedAccount> feedAccounts(UUID connectionId) {
        return feedAccounts.findByConnectionIdOrderByNameAsc(connectionId);
    }

    @Transactional(readOnly = true)
    public Optional<BankSyncRun> latestSync(UUID connectionId) {
        return syncRuns.findFirstByConnectionIdOrderByStartedAtDesc(connectionId);
    }

    /**
     * Saves verified credentials and the accounts they can see. Reconnecting the same provider
     * account reactivates the existing connection so linked accounts and history carry on.
     */
    @Transactional
    public BankConnection establish(
            UUID userId,
            BankFeedProvider provider,
            ConnectionMethod method,
            String externalUserId,
            Object credentials,
            List<FeedAccount> accountsSeen) {
        BankConnection connection = connections
                .findCurrent(userId, provider)
                .map(current -> {
                    if (!current.getExternalUserId().equals(externalUserId)) {
                        throw new ApiException(
                                ErrorCode.BANK_CONNECTION_DIFFERENT_USER,
                                "That is a different Akahu account from the one already connected. Disconnect it first.");
                    }
                    return current;
                })
                .or(() -> connections.findFirstByUserIdAndProviderAndExternalUserIdAndStatus(
                        userId, provider, externalUserId, ConnectionStatus.DISCONNECTED))
                .orElseGet(() -> new BankConnection(userId, provider, method, externalUserId));
        connection.activate(method, credentialStore.encrypt(connection.getId(), credentials));
        connections.save(connection);
        refresher.refresh(connection, accountsSeen);
        return connection;
    }

    /**
     * Turns syncing on or off for one account from the feed. Turning it on links the feed to a new
     * or existing account and starts an initial sync.
     */
    public BankFeedResponses.FeedAccountView updateFeedAccount(
            UUID userId, UUID connectionId, UUID feedAccountId, BankFeedRequests.UpdateFeedAccount request) {
        BankFeedAccount updated = transaction.execute(status -> {
            BankConnection connection = get(userId, connectionId);
            BankFeedAccount feedAccount = feedAccounts
                    .findByIdAndUserId(feedAccountId, userId)
                    .filter(a -> a.getConnectionId().equals(connectionId))
                    .orElseThrow(() -> ApiException.notFound("Bank feed account"));
            if (request.syncEnabled()) {
                if (!connection.isActive()) {
                    throw new ApiException(
                            ErrorCode.BANK_CONNECTION_NOT_ACTIVE, "Reconnect your bank before turning on syncing.");
                }
                enableSync(userId, feedAccount, request.linkAccountId());
            } else if (feedAccount.isSyncEnabled()) {
                feedAccount.disableSync();
                if (feedAccount.getAccountId() != null) {
                    accounts.releaseFromBankFeed(feedAccount.getAccountId());
                }
            }
            return feedAccounts.save(feedAccount);
        });
        if (request.syncEnabled()) {
            syncService.trySync(userId, connectionId, SyncTrigger.INITIAL);
        }
        return BankFeedResponses.FeedAccountView.from(updated);
    }

    private void enableSync(UUID userId, BankFeedAccount feedAccount, UUID linkAccountId) {
        Optional<Account> linked = Optional.ofNullable(feedAccount.getAccountId())
                .flatMap(id -> accounts.list(userId, false).stream()
                        .filter(a -> a.getId().equals(id))
                        .findFirst());
        Account account;
        if (linked.isPresent()) {
            account = linked.get();
            if (account.getManagedBy() == ManagedBy.USER) {
                accounts.attachToBankFeed(userId, account.getId());
            }
        } else if (linkAccountId != null) {
            if (feedAccounts.existsByUserIdAndAccountIdAndSyncEnabledTrue(userId, linkAccountId)) {
                throw new ApiException(
                        ErrorCode.BANK_FEED_ACCOUNT_LINK_INVALID, "That account already receives another bank feed.");
            }
            account = accounts.attachToBankFeed(userId, linkAccountId);
        } else {
            account = accounts.createForBankFeed(
                    userId,
                    feedAccount.getName(),
                    feedAccount.getType(),
                    feedAccount.getInstitution(),
                    feedAccount.getBalanceCents() == null ? 0 : feedAccount.getBalanceCents());
        }
        feedAccount.enableSync(account.getId());
        if (feedAccount.getBalanceCents() != null) {
            accounts.updateBalanceFromBankFeed(account.getId(), feedAccount.getBalanceCents());
        }
    }

    /**
     * Disconnects the provider. OAuth access is revoked with the provider first; if that fails the
     * local disconnection still goes ahead so the person is never stuck.
     */
    public void disconnect(UUID userId, UUID connectionId) {
        BankConnection connection = get(userId, connectionId);
        revokeQuietly(connection);
        transaction.executeWithoutResult(status -> {
            BankConnection current = get(userId, connectionId);
            for (BankFeedAccount feedAccount : feedAccounts.findByConnectionIdOrderByNameAsc(connectionId)) {
                if (feedAccount.isSyncEnabled()) {
                    feedAccount.disableSync();
                    if (feedAccount.getAccountId() != null) {
                        accounts.releaseFromBankFeed(feedAccount.getAccountId());
                    }
                }
            }
            current.disconnect(clock.instant());
        });
    }

    void revokeQuietly(BankConnection connection) {
        try {
            clients.forProvider(connection.getProvider()).revoke(connection);
        } catch (BankFeedException | IllegalStateException e) {
            log.warn(
                    "Could not revoke {} access for connection {}: {}",
                    connection.getProvider(),
                    connection.getId(),
                    e.getMessage());
        }
    }

    private BankFeedResponses.Connection view(BankConnection connection) {
        return BankFeedResponses.Connection.from(
                connection,
                feedAccounts.findByConnectionIdOrderByNameAsc(connection.getId()),
                syncRuns.findFirstByConnectionIdOrderByStartedAtDesc(connection.getId())
                        .orElse(null));
    }
}
