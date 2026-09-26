package nz.kiwifinance.analysis;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;
import nz.kiwifinance.account.Account;
import nz.kiwifinance.engine.analysis.CashflowAnalysis;
import nz.kiwifinance.engine.analysis.CategorySpending;
import nz.kiwifinance.engine.analysis.MonthSummary;
import nz.kiwifinance.engine.analysis.RecurringPayment;
import nz.kiwifinance.engine.analysis.TransactionRecord;
import nz.kiwifinance.engine.explain.Explanation;
import nz.kiwifinance.engine.money.Money;
import nz.kiwifinance.engine.time.MonthRange;
import nz.kiwifinance.user.UserProfile;

/**
 * A person's financial position, worked out once and shared by every feature that plans or
 * explains, so they all reason from the same numbers.
 *
 * @param window the complete months typical figures are drawn from
 * @param history transactions from the start of the window up to today
 * @param monthlyEssentials typical essential spending, used for the emergency fund
 */
public record FinancialSnapshot(
        UUID userId,
        LocalDate today,
        MonthRange window,
        List<TransactionRecord> history,
        CashflowAnalysis cashflow,
        MonthSummary thisMonth,
        List<CategorySpending> spending,
        List<RecurringPayment> recurring,
        Money monthlyIncome,
        IncomeBasis incomeBasis,
        Money monthlySurplus,
        Money monthlyEssentials,
        List<Account> accounts,
        Money liquidBalance,
        Money emergencyFundBalance,
        Money consumerDebt,
        Money netWorth,
        UserProfile profile) {

    /**
     * The same snapshot with a different usual monthly surplus, for asking "what if".
     */
    public FinancialSnapshot withMonthlySurplus(Money surplus) {
        return new FinancialSnapshot(
                userId,
                today,
                window,
                history,
                cashflow,
                thisMonth,
                spending,
                recurring,
                monthlyIncome,
                incomeBasis,
                surplus,
                monthlyEssentials,
                accounts,
                liquidBalance,
                emergencyFundBalance,
                consumerDebt,
                netWorth,
                profile);
    }

    public YearMonth currentMonth() {
        return YearMonth.from(today);
    }

    public int monthsOfData() {
        return cashflow.monthsOfData();
    }

    public List<CategorySpending> lifestyleSpending() {
        return spending.stream()
                .filter(s -> s.isUncategorised() || !s.category().group().isEssential())
                .toList();
    }

    /**
     * The assumptions behind the typical figures, for explanations.
     */
    public List<Explanation.Assumption> assumptions() {
        String incomeSource = switch (incomeBasis) {
            case TRANSACTIONS ->
                "Median of your last %d month%s of income.".formatted(monthsOfData(), monthsOfData() == 1 ? "" : "s");
            case INCOME_SOURCES ->
                "Based on the income sources you entered, because there isn't much transaction history yet.";
            case NONE -> "No income found yet. Add an income source or connect your bank.";
        };
        return List.of(new Explanation.Assumption("income_basis", "Income", incomeSource, null));
    }
}
