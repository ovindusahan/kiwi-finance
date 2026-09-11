package nz.kiwifinance.engine.nzrules;

import java.math.BigDecimal;
import nz.kiwifinance.engine.money.Money;

public record IndependentEarnerTaxCredit(
        Money annualAmount,
        Money minimumIncome,
        Money abatementThreshold,
        Money maximumIncome,
        BigDecimal abatementRate) {

    public Money annualCredit(Money annualIncome) {
        if (annualIncome.isLessThan(minimumIncome) || annualIncome.isGreaterThan(maximumIncome)) {
            return Money.ZERO;
        }
        Money abatement = annualIncome.isGreaterThan(abatementThreshold)
                ? annualIncome.minus(abatementThreshold).times(abatementRate)
                : Money.ZERO;
        return Money.max(Money.ZERO, annualAmount.minus(abatement));
    }
}
