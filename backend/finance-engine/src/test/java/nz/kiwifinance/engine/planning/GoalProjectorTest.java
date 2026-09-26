package nz.kiwifinance.engine.planning;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import nz.kiwifinance.engine.money.Money;
import org.junit.jupiter.api.Test;

class GoalProjectorTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 1);
    private final GoalProjector projector = new GoalProjector();

    @Test
    void flagsGoalsThatWillMissTheirDate() {
        var projection = projector.project(
                Money.ofDollars(10_000),
                Money.ofDollars(2_500),
                Money.ofDollars(500),
                TODAY.plusMonths(12),
                TODAY,
                BigDecimal.ZERO);

        assertThat(projection.status()).isEqualTo(GoalProjection.Status.BEHIND);
        assertThat(projection.monthsToGoal()).isEqualTo(15);
        assertThat(projection.requiredMonthly()).isEqualTo(Money.ofDollars(625));
        assertThat(projection.progress()).isEqualByComparingTo(new BigDecimal("0.25"));
        assertThat(projection.milestones())
                .filteredOn(GoalProjection.Milestone::reached)
                .hasSize(1);
        assertThat(projection.explanation().summary()).isEqualTo("To finish by October 2027, save $625 a month.");
    }

    @Test
    void recognisesAchievedGoals() {
        var projection = projector.project(
                Money.ofDollars(1_000), Money.ofDollars(1_200), Money.ZERO, null, TODAY, BigDecimal.ZERO);

        assertThat(projection.status()).isEqualTo(GoalProjection.Status.ACHIEVED);
        assertThat(projection.progress()).isEqualByComparingTo(BigDecimal.ONE);
    }

    @Test
    void notStartedWithoutSavingsOrContributions() {
        var projection =
                projector.project(Money.ofDollars(1_000), Money.ZERO, Money.ZERO, null, TODAY, BigDecimal.ZERO);

        assertThat(projection.status()).isEqualTo(GoalProjection.Status.NOT_STARTED);
    }
}
