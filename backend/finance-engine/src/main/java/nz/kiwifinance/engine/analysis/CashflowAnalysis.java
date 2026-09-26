package nz.kiwifinance.engine.analysis;

import java.math.BigDecimal;
import java.util.List;
import nz.kiwifinance.engine.money.Money;
import nz.kiwifinance.engine.time.MonthRange;

/**
 * @param months every month in the range, including empty ones
 * @param monthsOfData months from the first month with any activity to the end of the range
 * @param typicalIncome the average monthly income over the months with data, see
 *     {@link Stats#typical}
 * @param typicalSurplus typical income minus typical spending
 * @param incomeVariability coefficient of variation of monthly income
 */
public record CashflowAnalysis(
        MonthRange range,
        List<MonthSummary> months,
        int monthsOfData,
        Money typicalIncome,
        Money typicalSpending,
        Money typicalEssentialSpending,
        Money typicalLifestyleSpending,
        Money typicalSurplus,
        BigDecimal incomeVariability) {

    public CashflowAnalysis {
        months = List.copyOf(months);
    }

    public boolean hasEnoughData() {
        return monthsOfData >= 2;
    }

    public boolean hasVariableIncome() {
        return incomeVariability.compareTo(new BigDecimal("0.25")) > 0;
    }
}
