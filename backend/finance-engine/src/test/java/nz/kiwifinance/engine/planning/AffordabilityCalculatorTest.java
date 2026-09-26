package nz.kiwifinance.engine.planning;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import nz.kiwifinance.engine.analysis.CategoryGroup;
import nz.kiwifinance.engine.analysis.CategoryRef;
import nz.kiwifinance.engine.analysis.CategorySpending;
import nz.kiwifinance.engine.money.Money;
import nz.kiwifinance.engine.planning.AffordabilityResult.Lever;
import nz.kiwifinance.engine.planning.AffordabilityResult.Verdict;
import nz.kiwifinance.engine.time.PayFrequency;
import org.junit.jupiter.api.Test;

class AffordabilityCalculatorTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 1);
    private static final CategoryRef EATING_OUT = new CategoryRef("eating-out", "Eating out", CategoryGroup.LIFESTYLE);

    private final AffordabilityCalculator calculator = new AffordabilityCalculator();

    @Test
    void affordableNowWhenSavingsCoverThePriceAfterTheEmergencyFund() {
        var result = calculator.assess(request("a new laptop", 5_000, null, 20_000, 9_000, 1_500, null));

        assertThat(result.verdict()).isEqualTo(Verdict.AFFORDABLE_NOW);
        assertThat(result.availableNow()).isEqualTo(Money.ofDollars(11_000));
        assertThat(result.headline())
                .isEqualTo("Yes, you can afford a new laptop now and still have $6,000 left over.");
        assertThat(result.levers()).isEmpty();
    }

    @Test
    void onTrackWhenTheDesiredDateIsReachableAtTheCurrentPace() {
        var result =
                calculator.assess(request("a trip to Japan", 15_000, TODAY.plusMonths(12), 12_000, 9_000, 1_500, null));

        assertThat(result.verdict()).isEqualTo(Verdict.ON_TRACK);
        assertThat(result.requiredMonthlySaving()).isEqualTo(Money.ofDollars(1_000));
        assertThat(result.requiredPerPayPeriod()).isEqualTo(Money.ofDollars("461.54"));
        assertThat(result.headline()).contains("October 2027").contains("$462 a fortnight");
    }

    @Test
    void suggestsChangesWhenTheDesiredDateIsOutOfReach() {
        var result = calculator.assess(request("a car", 30_000, TODAY.plusMonths(12), 12_000, 9_000, 1_000, null));

        assertThat(result.verdict()).isEqualTo(Verdict.NEEDS_CHANGES);
        assertThat(result.shortfall()).isEqualTo(Money.ofDollars(27_000));
        assertThat(result.monthsNeeded()).isEqualTo(27);
        assertThat(result.realisticDate()).isEqualTo(YearMonth.of(2029, 1).atDay(1));
        assertThat(result.levers())
                .extracting(Lever::kind)
                .containsExactly(Lever.Kind.SAVE_MORE, Lever.Kind.REDUCE_SPENDING, Lever.Kind.MOVE_DATE);
        Lever saveMore = result.levers().getFirst();
        assertThat(saveMore.monthlyAmount()).isEqualTo(Money.ofDollars(1_250));
        Lever cutBack = result.levers().get(1);
        assertThat(cutBack.monthlyAmount()).isEqualTo(Money.ofDollars(150));
        // 27,000 at $1,150 a month takes 24 months instead of 27
        assertThat(cutBack.monthsSooner()).isEqualTo(3);
        assertThat(result.explanation().steps()).isNotEmpty();
    }

    @Test
    void outOfReachWhenNothingIsLeftOverEachMonth() {
        var result = calculator.assess(request("a holiday", 4_000, null, 9_000, 9_000, 0, null));

        assertThat(result.verdict()).isEqualTo(Verdict.OUT_OF_REACH);
        assertThat(result.monthsNeeded()).isNull();
        assertThat(result.realisticDate()).isNull();
    }

    @Test
    void explainsWhenGoalsAlreadyUseTheSurplus() {
        var base = request("a holiday", 4_000, null, 9_000, 9_000, 500, null);
        var committed = new AffordabilityRequest(
                base.itemName(),
                base.price(),
                null,
                TODAY,
                base.liquidBalance(),
                base.emergencyFundTarget(),
                Money.ZERO,
                base.monthlySurplus(),
                Money.ofDollars(600),
                BigDecimal.ZERO,
                PayFrequency.FORTNIGHTLY,
                base.lifestyleSpending(),
                Money.ZERO,
                null,
                6);

        var result = calculator.assess(committed);

        assertThat(result.verdict()).isEqualTo(Verdict.OUT_OF_REACH);
        assertThat(result.headline())
                .startsWith("Everything you have spare each month is already going to your other goals");
        assertThat(result.levers()).anyMatch(l -> l.kind() == Lever.Kind.PAUSE_GOALS && l.resultingDate() != null);
    }

    @Test
    void warnsWhenOnlyTheEmergencyFundCouldPayForIt() {
        var result = calculator.assess(request("a TV", 5_000, null, 10_000, 8_000, 500, null));

        assertThat(result.verdict()).isEqualTo(Verdict.SAVE_UP);
        assertThat(result.warnings()).anyMatch(w -> w.contains("emergency fund"));
    }

    @Test
    void checksRepaymentsFitWhenFinancing() {
        var financing =
                new AffordabilityRequest.Financing(Money.ofDollars(5_000), new BigDecimal("0.099"), 48, Money.ZERO);

        var result = calculator.assess(request("a car", 20_000, null, 15_000, 9_000, 300, financing));

        assertThat(result.target()).isEqualTo(Money.ofDollars(5_000));
        assertThat(result.loan().monthlyRepayment()).isEqualTo(Money.ofCents(37_972));
        assertThat(result.verdict()).isEqualTo(Verdict.NEEDS_CHANGES);
        assertThat(result.warnings()).anyMatch(w -> w.contains("Repayments of $380 a month"));
    }

    @Test
    void offersKiwiSaverForFirstHomes() {
        var base = request("a house deposit", 80_000, null, 30_000, 10_000, 2_000, null);
        var withKiwiSaver = new AffordabilityRequest(
                base.itemName(),
                base.price(),
                null,
                TODAY,
                base.liquidBalance(),
                base.emergencyFundTarget(),
                Money.ZERO,
                base.monthlySurplus(),
                Money.ZERO,
                BigDecimal.ZERO,
                PayFrequency.MONTHLY,
                List.of(),
                Money.ofDollars(25_000),
                null,
                6);

        var result = calculator.assess(withKiwiSaver);

        assertThat(result.levers()).anyMatch(l -> l.kind() == Lever.Kind.USE_KIWISAVER && l.monthsSooner() == 12);
    }

    private static AffordabilityRequest request(
            String item,
            long price,
            LocalDate desiredDate,
            long liquid,
            long emergencyFund,
            long surplus,
            AffordabilityRequest.Financing financing) {
        var eatingOut = new CategorySpending(
                EATING_OUT,
                Money.ofDollars(2_400),
                BigDecimal.ZERO,
                Money.ofDollars(400),
                Money.ofDollars(250),
                List.of());
        return new AffordabilityRequest(
                item,
                Money.ofDollars(price),
                desiredDate,
                TODAY,
                Money.ofDollars(liquid),
                Money.ofDollars(emergencyFund),
                Money.ZERO,
                Money.ofDollars(surplus),
                Money.ZERO,
                BigDecimal.ZERO,
                PayFrequency.FORTNIGHTLY,
                List.of(eatingOut),
                Money.ZERO,
                financing,
                6);
    }
}
