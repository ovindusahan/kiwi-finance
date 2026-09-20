package nz.kiwifinance.bankfeed;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import nz.kiwifinance.common.persistence.AuditableEntity;

@Entity
@Table(name = "bank_sync_runs")
@Getter
public class BankSyncRun extends AuditableEntity {

    @Column(name = "connection_id", nullable = false, updatable = false)
    private UUID connectionId;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "trigger_type", nullable = false, updatable = false)
    private SyncTrigger trigger;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SyncStatus status;

    @Column(name = "started_at", nullable = false, updatable = false)
    private Instant startedAt;

    @Column(name = "finished_at")
    private Instant finishedAt;

    @Column(name = "accounts_synced", nullable = false)
    private int accountsSynced;

    @Column(name = "transactions_created", nullable = false)
    private int transactionsCreated;

    @Column(name = "transactions_updated", nullable = false)
    private int transactionsUpdated;

    @Column(name = "error_code")
    private String errorCode;

    protected BankSyncRun() {}

    BankSyncRun(UUID connectionId, UUID userId, SyncTrigger trigger, Instant startedAt) {
        this.connectionId = connectionId;
        this.userId = userId;
        this.trigger = trigger;
        this.startedAt = startedAt;
        this.status = SyncStatus.RUNNING;
    }

    void succeed(Instant now, int accounts, int created, int updated) {
        status = SyncStatus.SUCCEEDED;
        finishedAt = now;
        accountsSynced = accounts;
        transactionsCreated = created;
        transactionsUpdated = updated;
    }

    void fail(Instant now, String errorCode, int accounts, int created, int updated) {
        status = SyncStatus.FAILED;
        finishedAt = now;
        this.errorCode = errorCode;
        accountsSynced = accounts;
        transactionsCreated = created;
        transactionsUpdated = updated;
    }
}
