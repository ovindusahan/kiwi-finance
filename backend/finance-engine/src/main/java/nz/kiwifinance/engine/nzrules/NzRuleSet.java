package nz.kiwifinance.engine.nzrules;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import nz.kiwifinance.engine.money.Money;
import nz.kiwifinance.engine.time.TaxYear;

/**
 * Every New Zealand rate and threshold the engine relies on for one tax year, with the
 * publications the values were taken from.
 */
public record NzRuleSet(
        TaxYear taxYear,
        List<TaxBracket> incomeTaxBrackets,
        Map<TaxCode, BigDecimal> secondaryTaxRates,
        AccEarnersLevy accEarnersLevy,
        StudentLoanRules studentLoan,
        IndependentEarnerTaxCredit independentEarnerTaxCredit,
        KiwiSaverRules kiwiSaver,
        List<TaxBracket> esctBrackets,
        LocalDate verifiedOn,
        List<String> sources) {

    public NzRuleSet {
        incomeTaxBrackets = List.copyOf(incomeTaxBrackets);
        secondaryTaxRates = Map.copyOf(secondaryTaxRates);
        esctBrackets = List.copyOf(esctBrackets);
        sources = List.copyOf(sources);
        if (incomeTaxBrackets.isEmpty() || !incomeTaxBrackets.getLast().isTopBracket()) {
            throw new IllegalArgumentException("Income tax brackets must end with an open top bracket");
        }
    }

    /**
     * Tax on annual income across the progressive brackets.
     */
    public Money incomeTax(Money annualIncome) {
        Money tax = Money.ZERO;
        Money lowerLimit = Money.ZERO;
        for (TaxBracket bracket : incomeTaxBrackets) {
            if (!annualIncome.isGreaterThan(lowerLimit)) {
                break;
            }
            Money upper = bracket.isTopBracket() ? annualIncome : Money.min(annualIncome, bracket.upperLimit());
            tax = tax.plus(upper.minus(lowerLimit).times(bracket.rate()));
            if (bracket.isTopBracket()) {
                break;
            }
            lowerLimit = bracket.upperLimit();
        }
        return tax;
    }

    /**
     * The highest rate that applies to the last dollar of the given income.
     */
    public BigDecimal marginalRate(Money annualIncome) {
        for (TaxBracket bracket : incomeTaxBrackets) {
            if (bracket.isTopBracket() || !annualIncome.isGreaterThan(bracket.upperLimit())) {
                return bracket.rate();
            }
        }
        throw new IllegalStateException("Unreachable: brackets end with a top bracket");
    }

    /**
     * ESCT is a flat rate on the employer contribution, chosen by the band that the employee's
     * earnings plus employer contributions fall into.
     */
    public BigDecimal esctRate(Money annualEarningsPlusContributions) {
        for (TaxBracket bracket : esctBrackets) {
            if (bracket.isTopBracket() || !annualEarningsPlusContributions.isGreaterThan(bracket.upperLimit())) {
                return bracket.rate();
            }
        }
        throw new IllegalStateException("ESCT brackets must end with a top bracket");
    }
}
