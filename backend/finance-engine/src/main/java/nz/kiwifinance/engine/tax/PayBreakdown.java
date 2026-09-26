package nz.kiwifinance.engine.tax;

import nz.kiwifinance.engine.explain.Explanation;
import nz.kiwifinance.engine.money.Money;
import nz.kiwifinance.engine.time.PayFrequency;
import nz.kiwifinance.engine.time.TaxYear;

public record PayBreakdown(
        TaxYear taxYear,
        PayFrequency frequency,
        Amounts perPeriod,
        Amounts annual,
        EmployerContribution employerKiwiSaver,
        Explanation explanation) {

    /**
     * @param incomeTax income tax after any Independent Earner Tax Credit
     */
    public record Amounts(
            Money gross,
            Money incomeTax,
            Money accLevy,
            Money studentLoan,
            Money kiwiSaver,
            Money independentEarnerTaxCredit,
            Money takeHome) {

        public Money totalDeductions() {
            return incomeTax.plus(accLevy).plus(studentLoan).plus(kiwiSaver);
        }
    }

    public record EmployerContribution(Money grossPerPeriod, Money esctPerPeriod, Money netPerPeriod) {}
}
