package nz.kiwifinance.engine.progress;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import nz.kiwifinance.engine.analysis.CashflowAnalyzer;
import nz.kiwifinance.engine.analysis.CategoryGroup;
import nz.kiwifinance.engine.analysis.CategoryRef;
import nz.kiwifinance.engine.analysis.TransactionRecord;
import nz.kiwifinance.engine.money.Money;
import nz.kiwifinance.engine.time.MonthRange;
import org.junit.jupiter.api.Test;

class ProgressTest {

    private static final CategoryRef EATING_OUT = new CategoryRef("e", "Eating out", CategoryGroup.LIFESTYLE);
    private static final CategoryRef RENT = new CategoryRef("r", "Rent", CategoryGroup.ESSENTIALS);
    private static final CategoryRef SALARY = new CategoryRef("s", "Salary", CategoryGroup.INCOME);
    private static final LocalDate TODAY = LocalDate.of(2026, 10, 15);

    @Test
    void countsNoSpendDaysSurplusMonthsAndWeeksUnderBudget() {
        List<TransactionRecord> transactions = new ArrayList<>();
        for (int month = 7; month <= 9; month++) {
            transactions.add(record(LocalDate.of(2026, month, 1), "4000", SALARY));
            transactions.add(record(LocalDate.of(2026, month, 2), "-2000", RENT));
        }
        transactions.add(record(LocalDate.of(2026, 10, 9), "-30", EATING_OUT));
        transactions.add(record(LocalDate.of(2026, 10, 10), "-1500", RENT));
        transactions.add(record(LocalDate.of(2026, 9, 30), "-500", EATING_OUT));
        var cashflow = new CashflowAnalyzer()
                .analyse(transactions, MonthRange.completeMonthsBefore(YearMonth.of(2026, 10), 3));

        Streaks streaks = new StreakCalculator().calculate(transactions, cashflow, Money.ofDollars(400), TODAY);

        assertThat(streaks.noSpendDays()).isEqualTo(5);
        assertThat(streaks.noSpendDaysThisMonth()).isEqualTo(13);
        assertThat(streaks.surplusMonths()).isEqualTo(3);
        // Weekly budget is $400 x 12 / 52 = $92.31; the week of 28 September had $500.
        assertThat(streaks.underBudgetWeeks()).isEqualTo(1);
    }

    @Test
    void evaluatesAchievementsWithProgress() {
        var facts = new AchievementFacts(
                true,
                true,
                true,
                false,
                Money.ofDollars(1_500),
                Money.ofDollars(3_000),
                new BigDecimal("0.25"),
                1,
                0,
                new BigDecimal("0.10"),
                3,
                new Streaks(7, 10, 1, 2));

        var achievements = new AchievementEvaluator().evaluate(facts);

        assertThat(achievements)
                .filteredOn(Achievement::unlocked)
                .extracting(Achievement::key)
                .containsExactly(
                        "first_steps", "connected", "budgeter", "first_thousand", "goal_setter", "no_spend_week");
        assertThat(achievements)
                .filteredOn(a -> a.key().equals("one_month_buffer"))
                .singleElement()
                .satisfies(a -> assertThat(a.progress()).isEqualByComparingTo("0.50"));
        assertThat(achievements)
                .filteredOn(a -> a.key().equals("super_saver"))
                .singleElement()
                .satisfies(a -> assertThat(a.progress()).isEqualByComparingTo("0.50"));
    }

    private static TransactionRecord record(LocalDate date, String amount, CategoryRef category) {
        return new TransactionRecord(date, Money.ofDollars(amount), category, null, "x", false);
    }
}
