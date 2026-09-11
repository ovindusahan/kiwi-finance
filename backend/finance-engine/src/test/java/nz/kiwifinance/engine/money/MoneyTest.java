package nz.kiwifinance.engine.money;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class MoneyTest {

    @Test
    void parsesDecimalDollarsWithoutFloatingPointError() {
        assertThat(Money.ofDollars("0.1").plus(Money.ofDollars("0.2"))).isEqualTo(Money.ofCents(30));
        assertThat(Money.ofDollars(new BigDecimal("1234.565"))).isEqualTo(Money.ofCents(123_457));
        assertThat(Money.ofDollars("-45.10")).isEqualTo(Money.ofCents(-4_510));
    }

    @Test
    void multipliesByRatesWithHalfUpRounding() {
        assertThat(Money.ofCents(1_000).times(new BigDecimal("0.175"))).isEqualTo(Money.ofCents(175));
        assertThat(Money.ofCents(333).times(new BigDecimal("0.5"))).isEqualTo(Money.ofCents(167));
    }

    @Test
    void allocatesEvenlyWithoutLosingCents() {
        assertThat(Money.ofCents(100).allocate(3))
                .containsExactly(Money.ofCents(34), Money.ofCents(33), Money.ofCents(33));
        assertThat(Money.ofCents(-100).allocate(3))
                .containsExactly(Money.ofCents(-34), Money.ofCents(-33), Money.ofCents(-33));
    }

    @Test
    void allocatesByWeightWithoutLosingCents() {
        var shares = Money.ofCents(1_000).allocate(1, 1, 1);
        assertThat(Money.sum(shares)).isEqualTo(Money.ofCents(1_000));
        assertThat(shares).containsExactly(Money.ofCents(334), Money.ofCents(333), Money.ofCents(333));

        assertThat(Money.ofCents(1_000).allocate(70, 30)).containsExactly(Money.ofCents(700), Money.ofCents(300));
    }

    @Test
    void roundsUpToIncrement() {
        assertThat(Money.ofDollars("431.20").roundUpTo(Money.ofDollars(10))).isEqualTo(Money.ofDollars(440));
        assertThat(Money.ofDollars(440).roundUpTo(Money.ofDollars(10))).isEqualTo(Money.ofDollars(440));
    }

    @Test
    void formatsAsNewZealandDollars() {
        assertThat(Money.ofCents(123_450).format()).isEqualTo("$1,234.50");
        assertThat(Money.ofCents(123_450).formatWhole()).isEqualTo("$1,235");
        assertThat(Money.ofCents(-5_000).format()).isEqualTo("-$50.00");
    }

    @Test
    void rejectsOverflow() {
        assertThatThrownBy(() -> Money.ofCents(Long.MAX_VALUE).plus(Money.ofCents(1)))
                .isInstanceOf(ArithmeticException.class);
    }
}
