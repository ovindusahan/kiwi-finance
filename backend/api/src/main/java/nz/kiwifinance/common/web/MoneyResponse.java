package nz.kiwifinance.common.web;

import io.swagger.v3.oas.annotations.media.Schema;
import nz.kiwifinance.engine.money.Money;

/**
 * Money on the wire: an integer number of cents and the currency, so clients never handle
 * floating-point amounts.
 */
@Schema(name = "Money")
public record MoneyResponse(long cents, String currency) {

    public static final String NZD = "NZD";

    public static MoneyResponse of(long cents) {
        return new MoneyResponse(cents, NZD);
    }

    public static MoneyResponse of(Money money) {
        return money == null ? null : new MoneyResponse(money.cents(), NZD);
    }
}
