package nz.kiwifinance.engine.planning;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import nz.kiwifinance.engine.money.Money;
import org.junit.jupiter.api.Test;

class PurchaseImpactCalculatorTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 2);

    private final PurchaseImpactCalculator calculator = new PurchaseImpactCalculator();

    @Test
    void costsACarWithTypicalFinanceAndRunningCosts() {
        PurchaseImpact impact = calculator.assess(request(PurchaseImpactRequest.Kind.CAR, 20_000, null, 3_000, false));

        assertThat(impact.deposit()).isEqualTo(Money.ofDollars(4_000));
        assertThat(impact.loan()).isNotNull();
        assertThat(impact.loan().principal()).isEqualTo(Money.ofDollars(16_000));
        assertThat(impact.loan().termMonths()).isEqualTo(60);
        assertThat(impact.monthlyCosts())
                .extracting(PurchaseImpact.CostLine::label)
                .containsExactly("Loan repayment", "Car insurance", "Registration and WOF", "Servicing and repairs");
        assertThat(impact.surplusAfter()).isEqualTo(Money.ofDollars(1_200).minus(impact.monthlyTotal()));
        assertThat(impact.cashNeeded()).isEqualTo(Money.ofDollars(4_190));
        assertThat(impact.cashShortfall()).isEqualTo(Money.ofDollars(1_190));
        assertThat(impact.monthsToSave()).isEqualTo(1);
        assertThat(impact.readyBy()).isEqualTo(TODAY.plusMonths(1));
        assertThat(impact.options())
                .extracting(PurchaseImpact.LoanOption::termMonths)
                .containsExactly(36, 48, 60, 84);
        assertThat(impact.options().get(0).monthlyRepayment())
                .isGreaterThan(impact.options().get(3).monthlyRepayment());
        assertThat(impact.verdict()).isEqualTo(PurchaseImpact.Verdict.COMFORTABLE);
        assertThat(impact.notes()).anyMatch(note -> note.contains("9.95%"));
    }

    @Test
    void countsRentAHomeWouldReplaceAndAFirstHomeKiwiSaver() {
        PurchaseImpactRequest request = new PurchaseImpactRequest(
                PurchaseImpactRequest.Kind.HOUSE,
                Money.ofDollars(600_000),
                Money.ofDollars(60_000),
                null,
                null,
                null,
                null,
                true,
                TODAY,
                Money.ofDollars(9_000),
                Money.ofDollars(2_000),
                Money.ofDollars(30_000),
                Money.ofDollars(41_000),
                Money.ofDollars(2_400));

        PurchaseImpact impact = calculator.assess(request);

        assertThat(impact.loan().annualRate()).isEqualByComparingTo(new BigDecimal("0.0599"));
        assertThat(impact.notes()).anyMatch(note -> note.contains("low-equity"));
        assertThat(impact.kiwiSaverAvailable()).isEqualTo(Money.ofDollars(40_000));
        assertThat(impact.monthlyCosts())
                .extracting(PurchaseImpact.CostLine::label)
                .contains("Rent you'd stop paying");
        assertThat(impact.cashNeeded()).isEqualTo(Money.ofDollars(64_550));
        assertThat(impact.cashShortfall()).isEqualTo(Money.ZERO);
        assertThat(impact.depositGuidance()).contains("10%", "First Home Loan");
        assertThat(impact.options()).hasSize(3);
    }

    @Test
    void saysWhenTheRepaymentsDoNotFit() {
        PurchaseImpact impact = calculator.assess(request(PurchaseImpactRequest.Kind.CAR, 90_000, 0L, 0, false));

        assertThat(impact.verdict()).isEqualTo(PurchaseImpact.Verdict.NOT_AFFORDABLE);
        assertThat(impact.headline()).contains("more than your budget can take");
    }

    @Test
    void paysCashForOtherPurchases() {
        PurchaseImpact impact = calculator.assess(request(PurchaseImpactRequest.Kind.OTHER, 2_400, null, 1_000, false));

        assertThat(impact.loan()).isNull();
        assertThat(impact.deposit()).isEqualTo(Money.ofDollars(2_400));
        assertThat(impact.monthlyCosts()).isEmpty();
        assertThat(impact.cashShortfall()).isEqualTo(Money.ofDollars(1_400));
        assertThat(impact.monthsToSave()).isEqualTo(2);
        assertThat(impact.verdict()).isEqualTo(PurchaseImpact.Verdict.COMFORTABLE);
    }

    private static PurchaseImpactRequest request(
            PurchaseImpactRequest.Kind kind, long price, Long deposit, long savings, boolean firstHome) {
        return new PurchaseImpactRequest(
                kind,
                Money.ofDollars(price),
                deposit == null ? null : Money.ofDollars(deposit),
                null,
                null,
                null,
                null,
                firstHome,
                TODAY,
                Money.ofDollars(6_000),
                Money.ofDollars(1_200),
                Money.ofDollars(savings),
                Money.ZERO,
                Money.ZERO);
    }
}
