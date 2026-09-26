package nz.kiwifinance.engine.planning;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import nz.kiwifinance.engine.explain.Explanation;
import nz.kiwifinance.engine.money.Money;

/**
 * @param requiredMonthly what to save each month to finish by the target date, or {@code null} if
 *     the goal has no target date
 */
public record GoalProjection(
        BigDecimal progress,
        Money remaining,
        Integer monthsToGoal,
        LocalDate projectedDate,
        Money requiredMonthly,
        Status status,
        List<Milestone> milestones,
        Explanation explanation) {

    public GoalProjection {
        milestones = List.copyOf(milestones);
    }

    public enum Status {
        ACHIEVED,
        ON_TRACK,
        BEHIND,
        NOT_STARTED
    }

    public record Milestone(int percent, Money amount, boolean reached) {}
}
