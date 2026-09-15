package nz.kiwifinance.engine.budgeting;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.List;
import nz.kiwifinance.engine.analysis.CategoryRef;
import nz.kiwifinance.engine.money.Money;

public record BudgetProgress(
        YearMonth month,
        int dayOfMonth,
        int daysInMonth,
        List<LineProgress> lines,
        Money totalLimit,
        Money totalSpent) {

    public BudgetProgress {
        lines = List.copyOf(lines);
    }

    public Money totalRemaining() {
        return totalLimit.minus(totalSpent);
    }

    public record LineProgress(
            CategoryRef category, Money limit, Money spent, Money remaining, BigDecimal usedFraction, Status status) {}

    public enum Status {
        ON_TRACK,
        AT_RISK,
        OVER
    }
}
