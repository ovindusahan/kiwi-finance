package nz.kiwifinance.engine.analysis;

import static nz.kiwifinance.engine.analysis.Fixtures.EATING_OUT;
import static nz.kiwifinance.engine.analysis.Fixtures.GROCERIES;
import static nz.kiwifinance.engine.analysis.Fixtures.earn;
import static nz.kiwifinance.engine.analysis.Fixtures.spend;
import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import nz.kiwifinance.engine.money.Money;
import nz.kiwifinance.engine.time.MonthRange;
import org.junit.jupiter.api.Test;

class SpendingAnalyzerTest {

    @Test
    void groupsSpendingByCategoryLargestFirst() {
        List<TransactionRecord> transactions = List.of(
                earn(LocalDate.of(2026, 7, 1), "5000"),
                spend(LocalDate.of(2026, 7, 2), "600", GROCERIES, "Countdown"),
                spend(LocalDate.of(2026, 8, 2), "600", GROCERIES, "Pak'nSave"),
                spend(LocalDate.of(2026, 9, 2), "600", GROCERIES, "New World"),
                spend(LocalDate.of(2026, 7, 9), "100", EATING_OUT, "Cafe"),
                spend(LocalDate.of(2026, 9, 9), "200", EATING_OUT, "Cafe"),
                spend(LocalDate.of(2026, 9, 10), "100", null, "Unknown"));

        var result = new SpendingAnalyzer().byCategory(transactions, MonthRange.endingWith(YearMonth.of(2026, 9), 3));

        assertThat(result)
                .extracting(s -> s.isUncategorised() ? "none" : s.category().id())
                .containsExactly("groceries", "eating-out", "none");
        CategorySpending eatingOut = result.get(1);
        assertThat(eatingOut.total()).isEqualTo(Money.ofDollars(300));
        assertThat(eatingOut.monthlyMedian()).isEqualTo(Money.ofDollars(100));
        assertThat(eatingOut.monthlyLowerQuartile()).isEqualTo(Money.ofDollars(50));
        assertThat(eatingOut.latestMonth()).isEqualTo(Money.ofDollars(200));
        assertThat(result.getFirst().share()).isEqualByComparingTo(new BigDecimal("0.818182"));
    }
}
