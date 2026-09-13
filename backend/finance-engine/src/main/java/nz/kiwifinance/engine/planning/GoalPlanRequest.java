package nz.kiwifinance.engine.planning;

import java.time.LocalDate;
import java.util.List;
import nz.kiwifinance.engine.money.Money;

/**
 * @param monthlySurplus what the person usually has left over each month
 * @param emergencyFundMonthly the suggested saving towards an emergency fund that is not yet
 *     funded, or zero
 */
public record GoalPlanRequest(LocalDate today, Money monthlySurplus, Money emergencyFundMonthly, List<Goal> goals) {

    public GoalPlanRequest {
        goals = List.copyOf(goals);
    }

    /**
     * @param priority lower numbers matter more
     * @param targetDate when the person wants it, or {@code null} for no deadline
     */
    public record Goal(
            String id, Money target, Money saved, LocalDate targetDate, int priority, Money monthlyContribution) {

        public Money remaining() {
            return Money.max(Money.ZERO, target.minus(saved));
        }
    }
}
