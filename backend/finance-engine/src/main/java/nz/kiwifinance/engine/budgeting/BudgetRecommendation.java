package nz.kiwifinance.engine.budgeting;

import java.math.BigDecimal;
import java.util.List;
import nz.kiwifinance.engine.analysis.CategoryRef;
import nz.kiwifinance.engine.explain.Explanation;
import nz.kiwifinance.engine.money.Money;

public record BudgetRecommendation(
        Money monthlyIncome,
        List<Line> lines,
        Money plannedSpending,
        Money plannedSavings,
        BigDecimal savingsRate,
        Money targetSavings,
        boolean meetsTarget,
        Explanation explanation) {

    public BudgetRecommendation {
        lines = List.copyOf(lines);
    }

    /**
     * @param category the category, or {@code null} for the allowance covering uncategorised spending
     * @param typical what the person usually spends in a month
     */
    public record Line(CategoryRef category, Kind kind, Money typical, Money recommended, String rationale) {

        public boolean isTrimmed() {
            return recommended.isLessThan(typical.roundUpTo(BudgetRecommender.ROUNDING));
        }
    }

    public enum Kind {
        ESSENTIAL,
        LIFESTYLE,
        UNCATEGORISED
    }
}
