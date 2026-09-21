package nz.kiwifinance.bankfeed;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import nz.kiwifinance.account.AccountType;
import nz.kiwifinance.common.web.MoneyResponse;
import org.jspecify.annotations.Nullable;

public final class BankFeedResponses {

    private BankFeedResponses() {}

    @Schema(name = "BankConnection")
    public record Connection(
            UUID id,
            BankFeedProvider provider,
            ConnectionMethod method,
            ConnectionStatus status,
            @Nullable Instant lastSyncedAt,
            Instant createdAt,
            List<FeedAccountView> accounts,
            @Nullable SyncRun latestSync) {

        static Connection from(BankConnection connection, List<BankFeedAccount> accounts, BankSyncRun latest) {
            return new Connection(
                    connection.getId(),
                    connection.getProvider(),
                    connection.getMethod(),
                    connection.getStatus(),
                    connection.getLastSyncedAt(),
                    connection.getCreatedAt(),
                    accounts.stream().map(FeedAccountView::from).toList(),
                    latest == null ? null : SyncRun.from(latest));
        }
    }

    @Schema(name = "BankFeedAccount")
    public record FeedAccountView(
            UUID id,
            String name,
            @Nullable String institution,
            AccountType type,
            @Nullable String maskedNumber,
            @Nullable MoneyResponse balance,
            @Nullable Instant balanceUpdatedAt,
            boolean supportsTransactions,
            boolean active,
            boolean syncEnabled,
            @Nullable UUID linkedAccountId,
            @Nullable Instant transactionsSyncedThrough) {

        static FeedAccountView from(BankFeedAccount account) {
            return new FeedAccountView(
                    account.getId(),
                    account.getName(),
                    account.getInstitution(),
                    account.getType(),
                    account.getMaskedNumber(),
                    account.getBalanceCents() == null ? null : MoneyResponse.of(account.getBalanceCents()),
                    account.getBalanceUpdatedAt(),
                    account.supportsTransactions(),
                    account.isActive(),
                    account.isSyncEnabled(),
                    account.getAccountId(),
                    account.getTransactionsSyncedThrough());
        }
    }

    @Schema(name = "BankSyncRun")
    public record SyncRun(
            UUID id,
            UUID connectionId,
            SyncTrigger trigger,
            SyncStatus status,
            Instant startedAt,
            @Nullable Instant finishedAt,
            int accountsSynced,
            int transactionsCreated,
            int transactionsUpdated,
            @Nullable String errorCode,
            @Nullable String errorMessage) {

        public static SyncRun from(BankSyncRun run) {
            return new SyncRun(
                    run.getId(),
                    run.getConnectionId(),
                    run.getTrigger(),
                    run.getStatus(),
                    run.getStartedAt(),
                    run.getFinishedAt(),
                    run.getAccountsSynced(),
                    run.getTransactionsCreated(),
                    run.getTransactionsUpdated(),
                    run.getErrorCode(),
                    messageFor(run.getErrorCode()));
        }

        private static String messageFor(String errorCode) {
            if (errorCode == null) {
                return null;
            }
            return switch (errorCode) {
                case "reauthorisation_required" ->
                    "Akahu stopped accepting your connection. Reconnect to keep syncing.";
                case "provider_rate_limited" ->
                    "Akahu asked us to slow down. We'll try again at the next scheduled sync.";
                case "provider_unavailable" -> "We couldn't reach Akahu. We'll try again at the next scheduled sync.";
                default -> "Something went wrong while syncing. We'll try again at the next scheduled sync.";
            };
        }
    }
}
