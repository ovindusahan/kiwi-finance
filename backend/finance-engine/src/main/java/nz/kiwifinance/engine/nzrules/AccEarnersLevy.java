package nz.kiwifinance.engine.nzrules;

import java.math.BigDecimal;
import nz.kiwifinance.engine.money.Money;

public record AccEarnersLevy(BigDecimal rate, Money maximumLiableEarnings) {

    public Money annualLevy(Money annualEarnings) {
        return Money.min(annualEarnings, maximumLiableEarnings).times(rate);
    }
}
