package nz.kiwifinance.engine.emergencyfund;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import nz.kiwifinance.engine.money.Money;
import org.junit.jupiter.api.Test;

class EmergencyFundCalculatorTest {

    private final EmergencyFundCalculator calculator = new EmergencyFundCalculator();

    @Test
    void targetsThreeMonthsOfEssentialsForAStableSituation() {
        var plan = calculator.plan(new EmergencyFundRequest(
                Money.ofDollars(3_120), Money.ofDollars(2_000), Money.ofDollars(1_000), false, false, false, false, 6));

        assertThat(plan.targetMonths()).isEqualTo(3);
        assertThat(plan.target()).isEqualTo(Money.ofDollars(9_400));
        assertThat(plan.shortfall()).isEqualTo(Money.ofDollars(7_400));
        assertThat(plan.status()).isEqualTo(EmergencyFundPlan.Status.BUILDING);
        assertThat(plan.monthsCovered()).isEqualByComparingTo(new BigDecimal("0.6"));
        assertThat(plan.suggestedMonthlyContribution()).isEqualTo(Money.ofDollars(500));
        assertThat(plan.monthsToTarget()).isEqualTo(15);
        assertThat(plan.milestones()).first().satisfies(m -> {
            assertThat(m.amount()).isEqualTo(Money.ofDollars(1_000));
            assertThat(m.reached()).isTrue();
        });
    }

    @Test
    void addsMonthsForRiskierSituationsUpToSix() {
        var plan = calculator.plan(new EmergencyFundRequest(
                Money.ofDollars(2_000), Money.ZERO, Money.ofDollars(3_000), true, true, true, true, 6));

        assertThat(plan.targetMonths()).isEqualTo(6);
        assertThat(plan.reasons()).hasSize(4);
        assertThat(plan.status()).isEqualTo(EmergencyFundPlan.Status.NOT_STARTED);
        assertThat(plan.suggestedMonthlyContribution()).isEqualTo(Money.ofDollars(1_000));
    }

    @Test
    void cannotSuggestSavingWithoutASurplus() {
        var plan = calculator.plan(new EmergencyFundRequest(
                Money.ofDollars(2_000), Money.ZERO, Money.ofDollars(-100), false, false, false, false, 3));

        assertThat(plan.suggestedMonthlyContribution()).isEqualTo(Money.ZERO);
        assertThat(plan.monthsToTarget()).isNull();
        assertThat(plan.explanation().summary()).contains("freeing up");
    }

    @Test
    void recognisesAFundedEmergencyFund() {
        var plan = calculator.plan(new EmergencyFundRequest(
                Money.ofDollars(2_000), Money.ofDollars(6_500), Money.ofDollars(500), false, false, false, false, 6));

        assertThat(plan.status()).isEqualTo(EmergencyFundPlan.Status.FUNDED);
        assertThat(plan.monthsToTarget()).isZero();
        assertThat(plan.progress()).isEqualByComparingTo(BigDecimal.ONE);
    }
}
