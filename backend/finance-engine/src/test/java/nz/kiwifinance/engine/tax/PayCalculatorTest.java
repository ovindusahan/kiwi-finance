package nz.kiwifinance.engine.tax;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import nz.kiwifinance.engine.money.Money;
import nz.kiwifinance.engine.nzrules.NzRules;
import nz.kiwifinance.engine.nzrules.TaxCode;
import nz.kiwifinance.engine.time.PayFrequency;
import org.junit.jupiter.api.Test;

class PayCalculatorTest {

    private static final LocalDate PAY_DATE_2026_27 = LocalDate.of(2026, 10, 1);
    private static final LocalDate PAY_DATE_2025_26 = LocalDate.of(2025, 10, 1);

    private final PayCalculator calculator = new PayCalculator(NzRules.standard());

    @Test
    void calculatesMonthlyTakeHomeForSeventyFiveThousandSalary() {
        var request = new PayRequest(
                Money.ofDollars(6_250),
                PayFrequency.MONTHLY,
                TaxCode.M,
                false,
                new BigDecimal("0.035"),
                PAY_DATE_2026_27);

        var result = calculator.calculate(request);

        // Annual tax: 15,600 x 10.5% + 37,900 x 17.5% + 21,500 x 30% = 14,720.50
        assertThat(result.annual().incomeTax()).isEqualTo(Money.ofDollars("14720.50"));
        assertThat(result.annual().accLevy()).isEqualTo(Money.ofDollars("1312.50"));
        assertThat(result.annual().kiwiSaver()).isEqualTo(Money.ofDollars(2_625));
        assertThat(result.annual().takeHome()).isEqualTo(Money.ofDollars(56_342));

        assertThat(result.perPeriod().incomeTax()).isEqualTo(Money.ofDollars("1226.71"));
        assertThat(result.perPeriod().accLevy()).isEqualTo(Money.ofDollars("109.38"));
        assertThat(result.perPeriod().kiwiSaver()).isEqualTo(Money.ofDollars("218.75"));
        assertThat(result.perPeriod().takeHome()).isEqualTo(Money.ofDollars("4695.16"));
        assertThat(result.taxYear().label()).isEqualTo("2026/27");
    }

    @Test
    void usesThePreviousYearsAccLevyBeforeFirstApril() {
        var request = new PayRequest(
                Money.ofDollars(75_000), PayFrequency.ANNUALLY, TaxCode.M, false, null, PAY_DATE_2025_26);

        assertThat(calculator.calculate(request).annual().accLevy()).isEqualTo(Money.ofDollars("1252.50"));
    }

    @Test
    void capsAccLevyAtMaximumLiableEarnings() {
        var request = new PayRequest(
                Money.ofDollars(250_000), PayFrequency.ANNUALLY, TaxCode.M, false, null, PAY_DATE_2026_27);

        // 156,641 x 1.75% = 2,741.2175
        assertThat(calculator.calculate(request).annual().accLevy()).isEqualTo(Money.ofDollars("2741.22"));
    }

    @Test
    void deductsStudentLoanAboveTheThreshold() {
        var request =
                new PayRequest(Money.ofDollars(75_000), PayFrequency.ANNUALLY, TaxCode.M, true, null, PAY_DATE_2026_27);

        // (75,000 - 24,128) x 12%
        assertThat(calculator.calculate(request).annual().studentLoan()).isEqualTo(Money.ofDollars("6104.64"));
    }

    @Test
    void appliesIndependentEarnerTaxCreditWithAbatement() {
        var full = new PayRequest(
                Money.ofDollars(50_000), PayFrequency.ANNUALLY, TaxCode.ME, false, null, PAY_DATE_2026_27);
        var abated = full.withGrossPay(Money.ofDollars(68_000));
        var ineligible = full.withGrossPay(Money.ofDollars(71_000));

        assertThat(calculator.calculate(full).annual().independentEarnerTaxCredit())
                .isEqualTo(Money.ofDollars(520));
        assertThat(calculator.calculate(abated).annual().independentEarnerTaxCredit())
                .isEqualTo(Money.ofDollars(260));
        assertThat(calculator.calculate(ineligible).annual().independentEarnerTaxCredit())
                .isEqualTo(Money.ZERO);
    }

    @Test
    void taxesSecondaryIncomeAtAFlatRate() {
        var request =
                new PayRequest(Money.ofDollars(500), PayFrequency.WEEKLY, TaxCode.SH, false, null, PAY_DATE_2026_27);

        assertThat(calculator.calculate(request).perPeriod().incomeTax()).isEqualTo(Money.ofDollars(150));
    }

    @Test
    void calculatesEmployerContributionAfterEsct() {
        var request = new PayRequest(
                Money.ofDollars(75_000),
                PayFrequency.ANNUALLY,
                TaxCode.M,
                false,
                new BigDecimal("0.06"),
                PAY_DATE_2026_27);

        var employer = calculator.calculate(request).employerKiwiSaver();

        // Employer pays the compulsory 3.5%, taxed at 30% ESCT for $77,625 of earnings plus contributions
        assertThat(employer.grossPerPeriod()).isEqualTo(Money.ofDollars(2_625));
        assertThat(employer.esctPerPeriod()).isEqualTo(Money.ofDollars("787.50"));
        assertThat(employer.netPerPeriod()).isEqualTo(Money.ofDollars("1837.50"));
    }

    @Test
    void findsGrossPayFromTakeHomePay() {
        var template = new PayRequest(
                Money.ZERO, PayFrequency.MONTHLY, TaxCode.M, false, new BigDecimal("0.035"), PAY_DATE_2026_27);

        var result = calculator.fromTakeHome(Money.ofDollars("4695.16"), template);

        assertThat(result.perPeriod()
                        .gross()
                        .minus(Money.ofDollars(6_250))
                        .abs()
                        .cents())
                .isLessThanOrEqualTo(2);
        assertThat(result.perPeriod().takeHome().cents()).isGreaterThanOrEqualTo(469_516);
    }

    @Test
    void explainsEachDeduction() {
        var request = new PayRequest(
                Money.ofDollars(1_500),
                PayFrequency.FORTNIGHTLY,
                TaxCode.M,
                true,
                new BigDecimal("0.035"),
                PAY_DATE_2026_27);

        var explanation = calculator.calculate(request).explanation();

        assertThat(explanation.summary()).contains("a fortnight");
        assertThat(explanation.steps())
                .extracting(step -> step.label())
                .contains("Income tax (PAYE)", "ACC earners' levy", "Student loan", "KiwiSaver (you)", "Take-home pay");
        assertThat(explanation.assumptions()).anyMatch(a -> a.value().contains("2026/27"));
    }
}
