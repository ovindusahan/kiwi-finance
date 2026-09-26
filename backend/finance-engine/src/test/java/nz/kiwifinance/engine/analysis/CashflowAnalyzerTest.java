package nz.kiwifinance.engine.analysis;

import static nz.kiwifinance.engine.analysis.Fixtures.EATING_OUT;
import static nz.kiwifinance.engine.analysis.Fixtures.GROCERIES;
import static nz.kiwifinance.engine.analysis.Fixtures.RENT;
import static nz.kiwifinance.engine.analysis.Fixtures.SAVINGS;
import static nz.kiwifinance.engine.analysis.Fixtures.earn;
import static nz.kiwifinance.engine.analysis.Fixtures.spend;
import static nz.kiwifinance.engine.analysis.Fixtures.transfer;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import nz.kiwifinance.engine.money.Money;
import nz.kiwifinance.engine.time.MonthRange;
import org.junit.jupiter.api.Test;

class CashflowAnalyzerTest {

    private final CashflowAnalyzer analyzer = new CashflowAnalyzer();

    @Test
    void summarisesIncomeSpendingAndSavingsPerMonth() {
        LocalDate day = LocalDate.of(2026, 9, 1);
        List<TransactionRecord> transactions = List.of(
                earn(day, "5000"),
                spend(day, "2000", RENT, "Landlord"),
                spend(day.plusDays(3), "450.50", GROCERIES, "Countdown"),
                spend(day.plusDays(4), "120", EATING_OUT, "Burger Fuel"),
                new TransactionRecord(day.plusDays(5), Money.ofDollars(20), EATING_OUT, "Burger Fuel", "REFUND", false),
                spend(day.plusDays(6), "300", SAVINGS, "Goal saver"),
                spend(day.plusDays(7), "75", null, "Mystery"),
                transfer(day.plusDays(8), "1000"));

        var result = analyzer.analyse(transactions, new MonthRange(YearMonth.of(2026, 9), YearMonth.of(2026, 9)));
        MonthSummary september = result.months().getFirst();

        assertThat(september.income()).isEqualTo(Money.ofDollars(5_000));
        assertThat(september.essentialSpending()).isEqualTo(Money.ofDollars("2450.50"));
        assertThat(september.lifestyleSpending()).isEqualTo(Money.ofDollars(100));
        assertThat(september.uncategorisedSpending()).isEqualTo(Money.ofDollars(75));
        assertThat(september.spending()).isEqualTo(Money.ofDollars("2625.50"));
        assertThat(september.saved()).isEqualTo(Money.ofDollars(300));
        assertThat(september.net()).isEqualTo(Money.ofDollars("2374.50"));
    }

    @Test
    void averagesFromTheFirstMonthWithDataIgnoringOneExtremeMonth() {
        List<TransactionRecord> transactions = new ArrayList<>();
        int[] spending = {3000, 3100, 9000, 3200};
        for (int i = 0; i < spending.length; i++) {
            LocalDate date = LocalDate.of(2026, 6 + i, 10);
            transactions.add(earn(date, "5000"));
            transactions.add(spend(date, String.valueOf(spending[i]), GROCERIES, "Countdown"));
        }

        var result = analyzer.analyse(transactions, new MonthRange(YearMonth.of(2026, 4), YearMonth.of(2026, 9)));

        assertThat(result.months()).hasSize(6);
        assertThat(result.monthsOfData()).isEqualTo(4);
        assertThat(result.typicalIncome()).isEqualTo(Money.ofDollars(5_000));
        // The $9,000 month is more than double the median, so the average uses the other three.
        assertThat(result.typicalSpending()).isEqualTo(Money.ofDollars(3_100));
        assertThat(result.typicalSurplus()).isEqualTo(Money.ofDollars(1_900));
        assertThat(result.hasVariableIncome()).isFalse();
    }

    @Test
    void reflectsFortnightlyPayCycles() {
        List<TransactionRecord> transactions = new ArrayList<>();
        for (LocalDate payday = LocalDate.of(2026, 4, 2);
                payday.isBefore(LocalDate.of(2026, 10, 1));
                payday = payday.plusDays(14)) {
            transactions.add(earn(payday, "2650"));
        }

        var result = analyzer.analyse(transactions, new MonthRange(YearMonth.of(2026, 4), YearMonth.of(2026, 9)));

        // 13 paydays over 6 months; a median would report two paydays a month instead.
        assertThat(result.typicalIncome()).isEqualTo(Money.ofDollars("5741.67"));
    }

    @Test
    void flagsVariableIncome() {
        List<TransactionRecord> transactions = List.of(
                earn(LocalDate.of(2026, 7, 1), "2000"),
                earn(LocalDate.of(2026, 8, 1), "7000"),
                earn(LocalDate.of(2026, 9, 1), "3500"));

        var result = analyzer.analyse(transactions, MonthRange.endingWith(YearMonth.of(2026, 9), 3));

        assertThat(result.hasVariableIncome()).isTrue();
    }
}
