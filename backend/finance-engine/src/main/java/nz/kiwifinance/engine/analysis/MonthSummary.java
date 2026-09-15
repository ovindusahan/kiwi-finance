package nz.kiwifinance.engine.analysis;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.YearMonth;
import nz.kiwifinance.engine.money.Money;

/**
 * Money in and out for one calendar month. Spending amounts are positive.
 *
 * @param saved money moved into savings categories, such as voluntary KiwiSaver or a goal account
 */
public record MonthSummary(
        YearMonth month,
        Money income,
        Money spending,
        Money essentialSpending,
        Money lifestyleSpending,
        Money uncategorisedSpending,
        Money saved,
        int transactionCount) {

    public Money net() {
        return income.minus(spending);
    }

    /**
     * The share of income not spent, as a fraction. Zero when there was no income.
     */
    public BigDecimal savingsRate() {
        if (!income.isPositive()) {
            return BigDecimal.ZERO;
        }
        return BigDecimal.valueOf(net().cents()).divide(BigDecimal.valueOf(income.cents()), 4, RoundingMode.HALF_UP);
    }

    public boolean hasActivity() {
        return transactionCount > 0;
    }
}
