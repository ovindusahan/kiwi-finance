package nz.kiwifinance.engine.emergencyfund;

import java.math.BigDecimal;
import java.util.List;
import nz.kiwifinance.engine.explain.Explanation;
import nz.kiwifinance.engine.money.Money;

/**
 * @param monthsCovered how many months of essential spending the current balance would cover
 * @param monthsToTarget months to reach the target at the suggested contribution, or {@code null}
 *     if there is no surplus to save from
 */
public record EmergencyFundPlan(
        Money monthlyEssentialSpending,
        int targetMonths,
        Money target,
        Money current,
        Money shortfall,
        BigDecimal progress,
        BigDecimal monthsCovered,
        Status status,
        Money suggestedMonthlyContribution,
        Integer monthsToTarget,
        List<Milestone> milestones,
        List<String> reasons,
        Explanation explanation) {

    public EmergencyFundPlan {
        milestones = List.copyOf(milestones);
        reasons = List.copyOf(reasons);
    }

    public enum Status {
        NOT_STARTED,
        BUILDING,
        FUNDED
    }

    public record Milestone(String label, Money amount, boolean reached) {}
}
