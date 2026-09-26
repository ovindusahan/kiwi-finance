package nz.kiwifinance.engine.budgeting;

import java.math.BigDecimal;
import java.util.List;
import nz.kiwifinance.engine.analysis.CategorySpending;
import nz.kiwifinance.engine.money.Money;

/**
 * @param spending per-category spending over the analysis window, from {@code SpendingAnalyzer}
 * @param targetSavingsRate the share of income the budget should leave unspent
 * @param goalContributions monthly amounts already promised to goals, which savings must cover
 */
public record BudgetRequest(
        Money monthlyIncome,
        List<CategorySpending> spending,
        int monthsOfData,
        BigDecimal targetSavingsRate,
        Money goalContributions) {

    public static final BigDecimal DEFAULT_SAVINGS_RATE = new BigDecimal("0.15");

    public BudgetRequest {
        spending = List.copyOf(spending);
    }
}
