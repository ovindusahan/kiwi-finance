package nz.kiwifinance.goal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import lombok.Getter;
import nz.kiwifinance.common.persistence.AuditableEntity;

@Entity
@Table(name = "goals")
@Getter
public class Goal extends AuditableEntity {

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "goal_type", nullable = false)
    private GoalType type;

    @Column(name = "target_cents", nullable = false)
    private long targetCents;

    @Column(name = "target_date")
    private LocalDate targetDate;

    @Column(nullable = false)
    private int priority;

    @Column(name = "monthly_contribution_cents", nullable = false)
    private long monthlyContributionCents;

    @Column(name = "linked_account_id")
    private UUID linkedAccountId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private GoalStatus status = GoalStatus.ACTIVE;

    @Column(name = "achieved_at")
    private Instant achievedAt;

    protected Goal() {}

    Goal(UUID userId) {
        this.userId = userId;
    }

    public boolean isActive() {
        return status == GoalStatus.ACTIVE;
    }

    void apply(GoalRequests.Save request) {
        name = request.name().trim();
        type = request.type();
        targetCents = request.targetCents();
        targetDate = request.targetDate();
        priority = request.priority() == null ? 2 : request.priority();
        monthlyContributionCents = request.monthlyContributionCents() == null ? 0 : request.monthlyContributionCents();
        linkedAccountId = request.linkedAccountId();
    }

    void changeStatus(GoalStatus status) {
        this.status = status;
        if (status != GoalStatus.ACHIEVED) {
            achievedAt = null;
        }
    }

    /**
     * Marks the goal achieved the first time its savings reach the target.
     */
    boolean markAchievedIfReached(long savedCents, Instant now) {
        if (status == GoalStatus.ACTIVE && savedCents >= targetCents) {
            status = GoalStatus.ACHIEVED;
            achievedAt = now;
            return true;
        }
        return false;
    }
}
