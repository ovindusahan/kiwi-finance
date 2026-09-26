package nz.kiwifinance.engine.analysis;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.List;
import nz.kiwifinance.engine.money.Money;

/**
 * Spending in one category over a range of months.
 *
 * @param category the category, or {@code null} for uncategorised spending
 * @param share this category's fraction of all spending in the range
 * @param monthly the amount spent in each month of the range, oldest first
 */
public record CategorySpending(
        CategoryRef category,
        Money total,
        BigDecimal share,
        Money monthlyMedian,
        Money monthlyLowerQuartile,
        List<MonthAmount> monthly) {

    public CategorySpending {
        monthly = List.copyOf(monthly);
    }

    public record MonthAmount(YearMonth month, Money amount) {}

    public Money latestMonth() {
        return monthly.isEmpty() ? Money.ZERO : monthly.getLast().amount();
    }

    public boolean isUncategorised() {
        return category == null;
    }
}
