package nz.kiwifinance.engine.planning;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import nz.kiwifinance.engine.money.Money;
import org.junit.jupiter.api.Test;

class SavingsProjectorTest {

    @Test
    void projectsWithoutInterest() {
        var projector = new SavingsProjector(BigDecimal.ZERO);

        assertThat(projector.futureValue(Money.ofDollars(500), Money.ofDollars(100), 12))
                .isEqualTo(Money.ofDollars(1_700));
        assertThat(projector.monthsToReach(Money.ofDollars(1_200), Money.ZERO, Money.ofDollars(100)))
                .hasValue(12);
        assertThat(projector.requiredMonthlyContribution(Money.ofDollars(1_000), Money.ZERO, 3))
                .isEqualTo(Money.ofCents(33_334));
    }

    @Test
    void compoundsInterestMonthly() {
        var projector = new SavingsProjector(new BigDecimal("0.03"));

        assertThat(projector.futureValue(Money.ZERO, Money.ofDollars(100), 12)).isEqualTo(Money.ofCents(121_664));
        Money required = projector.requiredMonthlyContribution(Money.ofCents(121_664), Money.ZERO, 12);
        assertThat(required.minus(Money.ofDollars(100)).cents()).isBetween(0L, 1L);
        assertThat(projector.futureValue(Money.ZERO, required, 12)).isGreaterThanOrEqualTo(Money.ofCents(121_664));
    }

    @Test
    void reportsUnreachableTargets() {
        var projector = new SavingsProjector(BigDecimal.ZERO);

        assertThat(projector.monthsToReach(Money.ofDollars(100), Money.ZERO, Money.ZERO))
                .isEmpty();
        assertThat(projector.monthsToReach(Money.ofDollars(100), Money.ofDollars(150), Money.ZERO))
                .hasValue(0);
    }
}
