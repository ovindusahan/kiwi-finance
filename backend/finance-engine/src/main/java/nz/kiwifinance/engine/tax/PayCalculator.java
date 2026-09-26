package nz.kiwifinance.engine.tax;

import java.math.BigDecimal;
import java.math.RoundingMode;
import lombok.RequiredArgsConstructor;
import nz.kiwifinance.engine.explain.Explanation;
import nz.kiwifinance.engine.money.Money;
import nz.kiwifinance.engine.nzrules.NzRuleSet;
import nz.kiwifinance.engine.nzrules.NzRules;
import nz.kiwifinance.engine.nzrules.TaxCode;
import nz.kiwifinance.engine.time.PayFrequency;
import nz.kiwifinance.engine.time.TaxYear;

/**
 * Estimates take-home pay. Deductions are calculated on the annualised income and spread evenly
 * across pay periods, which matches Inland Revenue's PAYE tables to within a few cents for regular
 * pay.
 */
@RequiredArgsConstructor
public final class PayCalculator {

    private static final Money MAX_GROSS_SEARCH = Money.ofDollars(10_000_000);

    private final NzRules rules;

    public PayBreakdown calculate(PayRequest request) {
        TaxYear taxYear = TaxYear.containing(request.payDate());
        NzRuleSet ruleSet = rules.forTaxYear(taxYear);
        PayFrequency frequency = request.frequency();
        Money annualGross = frequency.toAnnual(request.grossPay());

        Money annualIncomeTax = annualIncomeTax(request.taxCode(), annualGross, ruleSet);
        Money annualIetc = request.taxCode() == TaxCode.ME
                ? ruleSet.independentEarnerTaxCredit().annualCredit(annualGross)
                : Money.ZERO;
        Money annualAcc = ruleSet.accEarnersLevy().annualLevy(annualGross);
        Money annualStudentLoan = !request.studentLoan()
                ? Money.ZERO
                : request.taxCode().isSecondary()
                        ? ruleSet.studentLoan().secondaryRepayment(annualGross)
                        : ruleSet.studentLoan().annualRepayment(annualGross);
        Money annualKiwiSaver = request.isKiwiSaverMember() ? annualGross.times(request.kiwiSaverRate()) : Money.ZERO;

        Money periodGross = request.grossPay();
        Money periodIncomeTax = frequency.fromAnnual(annualIncomeTax);
        Money periodIetc = frequency.fromAnnual(annualIetc);
        Money periodAcc = frequency.fromAnnual(annualAcc);
        Money periodStudentLoan = frequency.fromAnnual(annualStudentLoan);
        Money periodKiwiSaver = frequency.fromAnnual(annualKiwiSaver);

        var perPeriod =
                amounts(periodGross, periodIncomeTax, periodIetc, periodAcc, periodStudentLoan, periodKiwiSaver);
        var annual = amounts(annualGross, annualIncomeTax, annualIetc, annualAcc, annualStudentLoan, annualKiwiSaver);
        var employer = employerContribution(request, ruleSet, annualGross);

        return new PayBreakdown(
                taxYear,
                frequency,
                perPeriod,
                annual,
                employer,
                explain(request, ruleSet, taxYear, perPeriod, annual, employer));
    }

    /**
     * Finds the gross pay that produces the given take-home pay, for people who only know what
     * lands in their bank account.
     */
    public PayBreakdown fromTakeHome(Money takeHome, PayRequest template) {
        Money low = takeHome;
        Money high = MAX_GROSS_SEARCH;
        while (high.minus(low).cents() > 1) {
            Money mid = Money.ofCents((low.cents() + high.cents()) / 2);
            if (calculate(template.withGrossPay(mid)).perPeriod().takeHome().isLessThan(takeHome)) {
                low = mid;
            } else {
                high = mid;
            }
        }
        return calculate(template.withGrossPay(high));
    }

    private static Money annualIncomeTax(TaxCode taxCode, Money annualGross, NzRuleSet ruleSet) {
        if (taxCode.isSecondary()) {
            return annualGross.times(ruleSet.secondaryTaxRates().get(taxCode));
        }
        return ruleSet.incomeTax(annualGross);
    }

    private static PayBreakdown.Amounts amounts(
            Money gross, Money incomeTax, Money ietc, Money acc, Money studentLoan, Money kiwiSaver) {
        Money taxAfterCredit = Money.max(Money.ZERO, incomeTax.minus(ietc));
        Money takeHome =
                gross.minus(taxAfterCredit).minus(acc).minus(studentLoan).minus(kiwiSaver);
        return new PayBreakdown.Amounts(gross, taxAfterCredit, acc, studentLoan, kiwiSaver, ietc, takeHome);
    }

    private static PayBreakdown.EmployerContribution employerContribution(
            PayRequest request, NzRuleSet ruleSet, Money annualGross) {
        if (!request.isKiwiSaverMember()) {
            return new PayBreakdown.EmployerContribution(Money.ZERO, Money.ZERO, Money.ZERO);
        }
        BigDecimal employerRate =
                request.kiwiSaverRate().min(ruleSet.kiwiSaver().compulsoryEmployerRate());
        Money annualEmployer = annualGross.times(employerRate);
        BigDecimal esctRate = ruleSet.esctRate(annualGross.plus(annualEmployer));
        PayFrequency frequency = request.frequency();
        Money grossPerPeriod = frequency.fromAnnual(annualEmployer);
        Money esctPerPeriod = grossPerPeriod.times(esctRate);
        return new PayBreakdown.EmployerContribution(
                grossPerPeriod, esctPerPeriod, grossPerPeriod.minus(esctPerPeriod));
    }

    private Explanation explain(
            PayRequest request,
            NzRuleSet ruleSet,
            TaxYear taxYear,
            PayBreakdown.Amounts perPeriod,
            PayBreakdown.Amounts annual,
            PayBreakdown.EmployerContribution employer) {
        String period = request.frequency().periodName();
        var builder = Explanation.builder()
                .summary("You take home about %s a %s from %s before deductions."
                        .formatted(
                                perPeriod.takeHome().format(),
                                period,
                                perPeriod.gross().format()))
                .step(
                        "Gross pay",
                        perPeriod.gross().format(),
                        "%s a year".formatted(annual.gross().formatWhole()))
                .step(
                        "Income tax (PAYE)",
                        "- " + perPeriod.incomeTax().format(),
                        request.taxCode().isSecondary()
                                ? "Secondary tax code %s taxes this income at a flat %s."
                                        .formatted(
                                                request.taxCode(),
                                                percent(ruleSet.secondaryTaxRates()
                                                        .get(request.taxCode())))
                                : "Progressive rates from 10.5%% to 39%%. Your top rate is %s."
                                        .formatted(percent(ruleSet.marginalRate(annual.gross()))))
                .step(
                        "ACC earners' levy",
                        "- " + perPeriod.accLevy().format(),
                        "%s of earnings up to %s a year."
                                .formatted(
                                        percent(ruleSet.accEarnersLevy().rate()),
                                        ruleSet.accEarnersLevy()
                                                .maximumLiableEarnings()
                                                .formatWhole()));
        if (perPeriod.independentEarnerTaxCredit().isPositive()) {
            builder.step(
                    "Independent Earner Tax Credit",
                    "+ " + perPeriod.independentEarnerTaxCredit().format(),
                    "Included because your tax code is ME.");
        }
        if (request.studentLoan()) {
            builder.step(
                    "Student loan",
                    "- " + perPeriod.studentLoan().format(),
                    "%s of income over %s a year."
                            .formatted(
                                    percent(ruleSet.studentLoan().repaymentRate()),
                                    ruleSet.studentLoan().annualThreshold().formatWhole()));
        }
        if (request.isKiwiSaverMember()) {
            builder.step(
                    "KiwiSaver (you)",
                    "- " + perPeriod.kiwiSaver().format(),
                    "%s of gross pay.".formatted(percent(request.kiwiSaverRate())));
            builder.step(
                    "KiwiSaver (your employer)",
                    "+ " + employer.netPerPeriod().format(),
                    "Paid into KiwiSaver on top of your pay, after employer superannuation contribution tax.");
        }
        builder.step("Take-home pay", perPeriod.takeHome().format());
        builder.assumption(
                "tax_year",
                "Tax rules",
                "Inland Revenue and ACC rates for the %s tax year%s"
                        .formatted(taxYear.label(), rules.isPublished(taxYear) ? "" : " (latest published rates)"),
                ruleSet.sources().getFirst());
        builder.assumption(
                "regular_pay",
                "Regular pay",
                "Deductions are worked out as if you earn this amount every %s for the whole year.".formatted(period),
                null);
        return builder.build();
    }

    static String percent(BigDecimal rate) {
        return rate.multiply(BigDecimal.valueOf(100))
                        .setScale(2, RoundingMode.HALF_UP)
                        .stripTrailingZeros()
                        .toPlainString()
                + "%";
    }
}
