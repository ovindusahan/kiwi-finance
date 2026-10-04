package nz.kiwifinance.bankfeed;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.stream.Collectors;
import lombok.extern.log4j.Log4j2;
import nz.kiwifinance.bankfeed.akahu.AkahuProperties;
import nz.kiwifinance.category.Category;
import nz.kiwifinance.category.CategoryService;
import nz.kiwifinance.common.error.ApiException;
import nz.kiwifinance.common.error.ErrorCode;
import nz.kiwifinance.transaction.ExternalTransaction;
import nz.kiwifinance.transaction.ImportResult;
import nz.kiwifinance.transaction.TransactionService;
import nz.kiwifinance.transaction.TransactionSource;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Pulls accounts and transactions from a provider into Kiwi Finance. Only one sync runs per
 * connection at a time, guarded by a lease in the database so this also holds across instances.
 * Provider calls happen outside database transactions; each account's import is committed on its
 * own so a failure part-way through keeps what was already synced.
 */
@Service
@Log4j2
public class BankSyncService {

    private final BankConnectionRepository connections;
    private final BankFeedAccountRepository feedAccounts;
    private final BankSyncRunRepository syncRuns;
    private final BankFeedClients clients;
    private final FeedAccountRefresher refresher;
    private final TransactionService transactionService;
    private final CategoryService categoryService;
    private final AkahuProperties properties;
    private final TransactionTemplate transaction;
    private final Executor executor;
    private final Clock clock;

    BankSyncService(
            BankConnectionRepository connections,
            BankFeedAccountRepository feedAccounts,
            BankSyncRunRepository syncRuns,
            BankFeedClients clients,
            FeedAccountRefresher refresher,
            TransactionService transactionService,
            CategoryService categoryService,
            AkahuProperties properties,
            TransactionTemplate transaction,
            @Qualifier("bankSyncExecutor") Executor executor,
            Clock clock) {
        this.connections = connections;
        this.feedAccounts = feedAccounts;
        this.syncRuns = syncRuns;
        this.clients = clients;
        this.refresher = refresher;
        this.transactionService = transactionService;
        this.categoryService = categoryService;
        this.properties = properties;
        this.transaction = transaction;
        this.executor = executor;
        this.clock = clock;
    }

    /**
     * Starts a sync in the background and returns the run so the caller can poll it.
     */
    public BankSyncRun requestSync(UUID userId, UUID connectionId, SyncTrigger trigger) {
        BankConnection connection = connections
                .findByIdAndUserId(connectionId, userId)
                .filter(c -> c.getStatus() != ConnectionStatus.DISCONNECTED)
                .orElseThrow(() -> ApiException.notFound("Bank connection"));
        if (!connection.isActive()) {
            throw new ApiException(ErrorCode.BANK_CONNECTION_NOT_ACTIVE, "Reconnect your bank to sync again.");
        }
        BankSyncRun run = start(connectionId, userId, trigger);
        if (run == null) {
            throw new ApiException(
                    ErrorCode.SYNC_IN_PROGRESS, "A sync is already running. It usually finishes within a minute.");
        }
        executor.execute(() -> execute(run.getId()));
        return run;
    }

    /**
     * Like {@link #requestSync} but does nothing if a sync is already running or the connection
     * cannot sync, for syncs that are a convenience rather than something the person asked for.
     */
    void trySync(UUID userId, UUID connectionId, SyncTrigger trigger) {
        try {
            requestSync(userId, connectionId, trigger);
        } catch (ApiException e) {
            log.debug("Skipped {} sync for connection {}: {}", trigger, connectionId, e.errorCode());
        }
    }

    /**
     * Syncs every active connection, one after another. Called by the scheduler.
     */
    public void syncAll() {
        for (UUID connectionId : connections.findActiveIds()) {
            try {
                UUID userId = connections
                        .findById(connectionId)
                        .map(BankConnection::getUserId)
                        .orElse(null);
                BankSyncRun run = userId == null ? null : start(connectionId, userId, SyncTrigger.SCHEDULED);
                if (run != null) {
                    execute(run.getId());
                }
            } catch (RuntimeException e) {
                log.error("Scheduled sync failed for connection {}", connectionId, e);
            }
        }
    }

    @Transactional(readOnly = true)
    public List<BankSyncRun> recentRuns(UUID userId, UUID connectionId, int limit) {
        connections.findByIdAndUserId(connectionId, userId).orElseThrow(() -> ApiException.notFound("Bank connection"));
        return syncRuns.findByConnectionIdOrderByStartedAtDesc(connectionId, Limit.of(Math.clamp(limit, 1, 50)));
    }

    @Transactional(readOnly = true)
    public BankSyncRun run(UUID userId, UUID runId) {
        return syncRuns.findByIdAndUserId(runId, userId).orElseThrow(() -> ApiException.notFound("Sync"));
    }

    private BankSyncRun start(UUID connectionId, UUID userId, SyncTrigger trigger) {
        return transaction.execute(status -> {
            Instant now = clock.instant();
            if (connections.acquireSyncLease(connectionId, now, now.plus(properties.syncLease())) == 0) {
                return null;
            }
            return syncRuns.save(new BankSyncRun(connectionId, userId, trigger, now));
        });
    }

    void execute(UUID runId) {
        BankSyncRun run = syncRuns.findById(runId).orElseThrow();
        UUID connectionId = run.getConnectionId();
        UUID userId = run.getUserId();
        int accountsSynced = 0;
        int created = 0;
        int updated = 0;
        boolean recorded = false;
        try {
            BankConnection connection = connections.findById(connectionId).orElseThrow();
            BankFeedClient client = clients.forProvider(connection.getProvider());
            Instant now = clock.instant();

            List<FeedAccount> seen = client.accounts(connection);
            transaction.executeWithoutResult(status -> refreshAccounts(connectionId, seen));

            Map<String, UUID> categoryBySlug = categoryService.list(userId).stream()
                    .filter(Category::isSystem)
                    .collect(Collectors.toMap(Category::getSlug, Category::getId));

            for (BankFeedAccount feedAccount : feedAccounts.findByConnectionIdOrderByNameAsc(connectionId)) {
                if (!feedAccount.isSyncEnabled()
                        || feedAccount.getAccountId() == null
                        || !feedAccount.supportsTransactions()
                        || !feedAccount.isActive()) {
                    continue;
                }
                Instant from = windowStart(feedAccount, now);
                List<FeedTransaction> items = client.transactions(connection, feedAccount.getExternalId(), from, now);
                ImportResult result = transaction.execute(status -> {
                    ImportResult imported = transactionService.importTransactions(
                            userId,
                            feedAccount.getAccountId(),
                            TransactionSource.BANK_FEED,
                            items.stream()
                                    .map(item -> toExternal(item, categoryBySlug))
                                    .toList());
                    feedAccounts.findById(feedAccount.getId()).ifPresent(account -> account.syncedThrough(now));
                    return imported;
                });
                accountsSynced++;
                created += result.created();
                updated += result.updated();
            }

            int finalAccounts = accountsSynced;
            int finalCreated = created;
            int finalUpdated = updated;
            transaction.executeWithoutResult(status -> {
                syncRuns.findById(runId)
                        .orElseThrow()
                        .succeed(clock.instant(), finalAccounts, finalCreated, finalUpdated);
                connections.findById(connectionId).orElseThrow().markSynced(now);
                // Released with the result, so a sync that looks finished can always be followed by another.
                connections.releaseSyncLease(connectionId);
            });
            recorded = true;
        } catch (BankFeedException e) {
            log.warn("Sync {} for connection {} failed: {}", runId, connectionId, e.getMessage());
            fail(
                    runId,
                    connectionId,
                    e.errorCode(),
                    e.reason() == BankFeedException.Reason.UNAUTHORISED,
                    accountsSynced,
                    created,
                    updated);
            recorded = true;
        } catch (RuntimeException e) {
            log.error("Sync {} for connection {} failed unexpectedly", runId, connectionId, e);
            fail(runId, connectionId, "internal_error", false, accountsSynced, created, updated);
            recorded = true;
        } finally {
            // Recording the outcome releases the lease. Only release it here if that never happened,
            // so this can't free a lease a newer sync has since taken.
            if (!recorded) {
                transaction.executeWithoutResult(status -> connections.releaseSyncLease(connectionId));
            }
        }
    }

    /**
     * The first sync reaches back over the configured history. Later syncs re-read a few days
     * before the last sync, because banks sometimes post transactions days after they happen.
     */
    private Instant windowStart(BankFeedAccount account, Instant now) {
        Instant syncedThrough = account.getTransactionsSyncedThrough();
        if (syncedThrough == null) {
            return now.minus(Duration.ofDays(properties.initialHistoryDays()));
        }
        return syncedThrough.minus(Duration.ofDays(properties.resyncOverlapDays()));
    }

    private void refreshAccounts(UUID connectionId, List<FeedAccount> seen) {
        refresher.refresh(connections.findById(connectionId).orElseThrow(), seen);
    }

    private static ExternalTransaction toExternal(FeedTransaction item, Map<String, UUID> categoryBySlug) {
        return new ExternalTransaction(
                item.externalId(),
                item.postedOn(),
                item.amountCents(),
                item.description(),
                item.merchant(),
                item.suggestedCategorySlug() == null ? null : categoryBySlug.get(item.suggestedCategorySlug()),
                item.transfer());
    }

    private void fail(
            UUID runId, UUID connectionId, String code, boolean unauthorised, int accounts, int created, int updated) {
        transaction.executeWithoutResult(status -> {
            syncRuns.findById(runId).orElseThrow().fail(clock.instant(), code, accounts, created, updated);
            if (unauthorised) {
                connections.findById(connectionId).orElseThrow().requireReauthorisation();
            }
            connections.releaseSyncLease(connectionId);
        });
    }
}
