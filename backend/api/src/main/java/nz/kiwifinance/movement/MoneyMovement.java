package nz.kiwifinance.movement;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.util.UUID;
import lombok.Getter;
import nz.kiwifinance.common.persistence.AuditableEntity;

/**
 * A transfer, withdrawal or deposit the person recorded in Kiwi Finance.
 */
@Entity
@Table(name = "money_movements")
@Getter
class MoneyMovement extends AuditableEntity {

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "movement_type", nullable = false, updatable = false)
    private MovementType type;

    @Column(name = "from_account_id", updatable = false)
    private UUID fromAccountId;

    @Column(name = "to_account_id", updatable = false)
    private UUID toAccountId;

    @Column(name = "amount_cents", nullable = false, updatable = false)
    private long amountCents;

    @Column(name = "moved_on", nullable = false, updatable = false)
    private LocalDate movedOn;

    private String note;

    protected MoneyMovement() {}

    MoneyMovement(
            UUID userId,
            MovementType type,
            UUID fromAccountId,
            UUID toAccountId,
            long amountCents,
            LocalDate movedOn,
            String note) {
        this.userId = userId;
        this.type = type;
        this.fromAccountId = fromAccountId;
        this.toAccountId = toAccountId;
        this.amountCents = amountCents;
        this.movedOn = movedOn;
        this.note = note;
    }
}
