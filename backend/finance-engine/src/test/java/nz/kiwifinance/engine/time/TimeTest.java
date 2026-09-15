package nz.kiwifinance.engine.time;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.YearMonth;
import nz.kiwifinance.engine.money.Money;
import org.junit.jupiter.api.Test;

class TimeTest {

    @Test
    void taxYearRunsFromAprilToMarch() {
        assertThat(TaxYear.containing(LocalDate.of(2026, 3, 31))).isEqualTo(new TaxYear(2025));
        assertThat(TaxYear.containing(LocalDate.of(2026, 4, 1))).isEqualTo(new TaxYear(2026));
        assertThat(new TaxYear(2026).label()).isEqualTo("2026/27");
        assertThat(new TaxYear(2026).lastDay()).isEqualTo(LocalDate.of(2027, 3, 31));
    }

    @Test
    void convertsBetweenPayFrequenciesThroughTheAnnualAmount() {
        assertThat(PayFrequency.WEEKLY.convert(Money.ofDollars(100), PayFrequency.MONTHLY))
                .isEqualTo(Money.ofDollars("433.33"));
        assertThat(PayFrequency.MONTHLY.convert(Money.ofDollars(1_300), PayFrequency.FORTNIGHTLY))
                .isEqualTo(Money.ofDollars(600));
        assertThat(PayFrequency.FOUR_WEEKLY.toAnnual(Money.ofDollars(1_000))).isEqualTo(Money.ofDollars(13_000));
    }

    @Test
    void completeMonthsExcludeTheCurrentMonth() {
        var range = MonthRange.completeMonthsBefore(YearMonth.of(2026, 10), 6);
        assertThat(range.first()).isEqualTo(YearMonth.of(2026, 4));
        assertThat(range.last()).isEqualTo(YearMonth.of(2026, 9));
        assertThat(range.size()).isEqualTo(6);
        assertThat(range.months()).hasSize(6);
        assertThat(range.contains(LocalDate.of(2026, 10, 1))).isFalse();
    }
}
