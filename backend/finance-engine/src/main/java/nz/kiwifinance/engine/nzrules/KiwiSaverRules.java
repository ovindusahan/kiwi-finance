package nz.kiwifinance.engine.nzrules;

import java.math.BigDecimal;
import java.util.List;
import nz.kiwifinance.engine.money.Money;

/**
 * KiwiSaver settings for a tax year. The government contribution is assessed over the KiwiSaver
 * year (1 July to 30 June) rather than the tax year; the values here are those in force for most
 * of the tax year.
 */
public record KiwiSaverRules(
        List<BigDecimal> employeeRates,
        BigDecimal defaultRate,
        BigDecimal compulsoryEmployerRate,
        BigDecimal governmentContributionRate,
        Money governmentContributionMaximum,
        Money governmentContributionIncomeCap,
        int firstHomeMinimumMembershipYears,
        Money firstHomeMinimumRemainingBalance) {

    public KiwiSaverRules {
        employeeRates = List.copyOf(employeeRates);
    }

    public boolean isPermittedEmployeeRate(BigDecimal rate) {
        return employeeRates.stream().anyMatch(permitted -> permitted.compareTo(rate) == 0);
    }

    public Money annualGovernmentContribution(Money annualMemberContributions, Money annualIncome) {
        if (annualIncome.isGreaterThan(governmentContributionIncomeCap)) {
            return Money.ZERO;
        }
        return Money.min(annualMemberContributions.times(governmentContributionRate), governmentContributionMaximum);
    }
}
