package nz.kiwifinance.goal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import nz.kiwifinance.common.persistence.AuditableEntity;

/**
 * Money put towards, or taken back from, a goal that isn't linked to its own account.
 */
@Entity
@Table(name = "goal_contributions")
class GoalContribution extends AuditableEntity {

    @Column(name = "goal_id", nullable = false, updatable = false)
    private UUID goalId;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Getter(AccessLevel.PACKAGE)
    @Column(name = "amount_cents", nullable = false, updatable = false)
    private long amountCents;

    @Getter(AccessLevel.PACKAGE)
    @Column(name = "contributed_on", nullable = false, updatable = false)
    private LocalDate contributedOn;

    @Getter(AccessLevel.PACKAGE)
    @Column(updatable = false)
    private String note;

    protected GoalContribution() {}

    GoalContribution(UUID goalId, UUID userId, long amountCents, LocalDate contributedOn, String note) {
        this.goalId = goalId;
        this.userId = userId;
        this.amountCents = amountCents;
        this.contributedOn = contributedOn;
        this.note = note;
    }
}
