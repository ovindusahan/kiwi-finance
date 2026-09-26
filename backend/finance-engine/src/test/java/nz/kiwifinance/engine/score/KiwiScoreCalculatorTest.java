package nz.kiwifinance.engine.score;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import nz.kiwifinance.engine.money.Money;
import org.junit.jupiter.api.Test;

class KiwiScoreCalculatorTest {

    private final KiwiScoreCalculator calculator = new KiwiScoreCalculator();

    @Test
    void weighsEachPartOfFinancialWellbeing() {
        var score = calculator.calculate(new KiwiScoreRequest(
                new BigDecimal("0.10"), new BigDecimal("0.5"), 4, 6, 5, 6, Money.ZERO, Money.ofDollars(5_000), 1, 1));

        // (50 x 25 + 50 x 25 + 66 x 15 + 83 x 15 + 100 x 10 + 100 x 10) / 100
        assertThat(score.score()).isEqualTo(67);
        assertThat(score.band()).isEqualTo(KiwiScore.Band.SOLID);
        assertThat(score.components()).extracting(KiwiScore.Component::score).containsExactly(50, 50, 66, 83, 100, 100);
        assertThat(score.nextStep()).contains("20%");
    }

    @Test
    void scoresDebtAgainstIncome() {
        var score = calculator.calculate(new KiwiScoreRequest(
                new BigDecimal("0.25"),
                BigDecimal.ONE,
                6,
                6,
                null,
                null,
                Money.ofDollars(7_500),
                Money.ofDollars(5_000),
                0,
                0));

        var debt = score.components().stream()
                .filter(c -> c.key().equals("debt"))
                .findFirst()
                .orElseThrow();
        assertThat(debt.score()).isEqualTo(50);
        assertThat(debt.detail()).contains("$7,500");
        var budget = score.components().stream()
                .filter(c -> c.key().equals("budget"))
                .findFirst()
                .orElseThrow();
        assertThat(budget.score()).isEqualTo(40);
    }

    @Test
    void treatsOverspendingAsZeroSavings() {
        var score = calculator.calculate(new KiwiScoreRequest(
                new BigDecimal("-0.1"), BigDecimal.ZERO, 0, 6, null, null, Money.ZERO, Money.ofDollars(4_000), 0, 0));

        assertThat(score.components().getFirst().score()).isZero();
        assertThat(score.band()).isEqualTo(KiwiScore.Band.GETTING_STARTED);
    }
}
