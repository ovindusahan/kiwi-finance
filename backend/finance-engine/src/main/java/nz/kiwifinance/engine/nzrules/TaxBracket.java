package nz.kiwifinance.engine.nzrules;

import java.math.BigDecimal;
import nz.kiwifinance.engine.money.Money;

/**
 * A band of annual income taxed at one rate. The top band has no upper limit.
 */
public record TaxBracket(Money upperLimit, BigDecimal rate) {

    public static TaxBracket upTo(long dollars, String rate) {
        return new TaxBracket(Money.ofDollars(dollars), new BigDecimal(rate));
    }

    public static TaxBracket above(String rate) {
        return new TaxBracket(null, new BigDecimal(rate));
    }

    public boolean isTopBracket() {
        return upperLimit == null;
    }
}
