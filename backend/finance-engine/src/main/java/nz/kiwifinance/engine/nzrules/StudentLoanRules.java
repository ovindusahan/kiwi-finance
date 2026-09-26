package nz.kiwifinance.engine.nzrules;

import java.math.BigDecimal;
import nz.kiwifinance.engine.money.Money;

public record StudentLoanRules(BigDecimal repaymentRate, Money annualThreshold) {

    public Money annualRepayment(Money annualIncome) {
        Money liable = annualIncome.minus(annualThreshold);
        return liable.isPositive() ? liable.times(repaymentRate) : Money.ZERO;
    }

    /**
     * Secondary employment has no threshold: the repayment rate applies to every dollar.
     */
    public Money secondaryRepayment(Money annualIncome) {
        return annualIncome.times(repaymentRate);
    }
}
