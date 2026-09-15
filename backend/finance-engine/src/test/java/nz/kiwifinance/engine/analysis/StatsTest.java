package nz.kiwifinance.engine.analysis;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import nz.kiwifinance.engine.money.Money;
import org.junit.jupiter.api.Test;

class StatsTest {

    @Test
    void calculatesMediansAndPercentiles() {
        List<Money> values = dollars(400, 100, 300, 200);

        assertThat(Stats.median(values)).isEqualTo(Money.ofDollars(250));
        assertThat(Stats.percentile(values, 25)).isEqualTo(Money.ofDollars(175));
        assertThat(Stats.median(List.of())).isEqualTo(Money.ZERO);
    }

    @Test
    void typicalIgnoresOneMonthMoreThanDoubleTheMedian() {
        assertThat(Stats.typical(dollars(100, 100, 150, 100))).isEqualTo(Money.ofDollars("112.50"));
        assertThat(Stats.typical(dollars(100, 100, 500, 100))).isEqualTo(Money.ofDollars(100));
        assertThat(Stats.typical(dollars(100, 300))).isEqualTo(Money.ofDollars(200));
    }

    @Test
    void measuresVariability() {
        assertThat(Stats.coefficientOfVariation(dollars(100, 100, 100))).isEqualByComparingTo("0");
        assertThat(Stats.coefficientOfVariation(dollars(100, 300)).doubleValue())
                .isEqualTo(0.5);
    }

    private static List<Money> dollars(long... values) {
        return java.util.Arrays.stream(values).mapToObj(Money::ofDollars).toList();
    }
}
