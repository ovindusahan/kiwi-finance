package nz.kiwifinance.bankfeed;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import nz.kiwifinance.account.AccountType;
import nz.kiwifinance.common.persistence.AuditableEntity;

/**
 * An account as reported by the bank feed. It becomes part of the person's accounts only once they
 * choose to sync it, at which point it is linked to a local account.
 */
@Entity
@Table(name = "bank_feed_accounts")
@Getter
public class BankFeedAccount extends AuditableEntity {

    @Column(name = "connection_id", nullable = false, updatable = false)
    private UUID connectionId;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "external_id", nullable = false, updatable = false)
    private String externalId;

    @Column(nullable = false)
    private String name;

    private String institution;

    @Enumerated(EnumType.STRING)
    @Column(name = "account_type", nullable = false)
    private AccountType type;

    @Column(name = "masked_number")
    private String maskedNumber;

    @Column(name = "balance_cents")
    private Long balanceCents;

    @Column(name = "balance_updated_at")
    private Instant balanceUpdatedAt;

    @Column(name = "supports_transactions", nullable = false)
    private boolean supportsTransactions;

    @Column(name = "is_active", nullable = false)
    private boolean active;

    @Column(name = "account_id")
    private UUID accountId;

    @Column(name = "sync_enabled", nullable = false)
    private boolean syncEnabled;

    @Column(name = "transactions_synced_through")
    private Instant transactionsSyncedThrough;

    protected BankFeedAccount() {}

    BankFeedAccount(UUID connectionId, UUID userId, String externalId) {
        this.connectionId = connectionId;
        this.userId = userId;
        this.externalId = externalId;
    }

    public boolean supportsTransactions() {
        return supportsTransactions;
    }

    void refresh(FeedAccount feed) {
        name = feed.name();
        institution = feed.institution();
        type = feed.type();
        maskedNumber = feed.maskedNumber();
        balanceCents = feed.balanceCents();
        balanceUpdatedAt = feed.balanceUpdatedAt();
        supportsTransactions = feed.supportsTransactions();
        active = feed.active();
    }

    void enableSync(UUID accountId) {
        this.accountId = accountId;
        this.syncEnabled = true;
    }

    void disableSync() {
        this.syncEnabled = false;
    }

    void syncedThrough(Instant instant) {
        this.transactionsSyncedThrough = instant;
    }
}
