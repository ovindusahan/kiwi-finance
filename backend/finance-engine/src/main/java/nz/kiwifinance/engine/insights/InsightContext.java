package nz.kiwifinance.engine.insights;

import java.time.LocalDate;
import java.util.List;
import nz.kiwifinance.engine.analysis.CategoryRef;
import nz.kiwifinance.engine.analysis.MonthSummary;
import nz.kiwifinance.engine.money.Money;
import nz.kiwifinance.engine.planning.GoalPlan;

/**
 * The person's current situation beyond their spending history, so insights can speak to their
 * budget, goals, debt and accounts as they stand today.
 *
 * @param spendable money in accounts they can reach quickly, outside the emergency fund
 * @param consumerDebt what they owe on cards and loans, as a positive amount
 * @param emergencyFundWithdrawn how much more left the emergency fund account than arrived in the
 *     last 30 days, or zero
 * @param budget the active budget for this month, or {@code null} without one
 * @param goals suggestions for the person's goals, most urgent first
 */
public record InsightContext(
        LocalDate today,
        MonthSummary thisMonth,
        Money spendable,
        Money consumerDebt,
        boolean emergencyFundChosen,
        Money emergencyFundWithdrawn,
        Budget budget,
        List<GoalNote> goals) {

    public InsightContext {
        goals = List.copyOf(goals);
    }

    public record Budget(int dayOfMonth, int daysInMonth, Money totalLimit, Money totalSpent, List<Line> lines) {

        public Budget {
            lines = List.copyOf(lines);
        }

        public double pace() {
            return daysInMonth == 0 ? 1 : (double) dayOfMonth / daysInMonth;
        }
    }

    /**
     * @param category the category, or {@code null} for the allowance covering everything else
     */
    public record Line(CategoryRef category, Money limit, Money spent) {}

    public record GoalNote(String name, GoalPlan.Kind kind, GoalPlan.Urgency urgency, String title, String detail) {}
}
