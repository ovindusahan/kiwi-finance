package nz.kiwifinance.engine.planning;

import java.math.BigDecimal;
import java.time.LocalDate;
import nz.kiwifinance.engine.money.Money;

/**
 * A big purchase the person is considering, and their situation today.
 *
 * @param deposit cash put towards the price; {@code null} uses the usual deposit for the kind
 * @param annualRate the loan's interest rate; {@code null} uses a typical New Zealand rate
 * @param termMonths the loan term; {@code null} uses a typical term; zero means paying cash
 * @param upfrontCosts one-off costs on top of the price; {@code null} uses typical costs
 * @param ownershipCosts running costs each month once bought; {@code null} uses typical costs
 * @param monthlyIncome take-home pay in a typical month
 * @param monthlySurplus what is usually left over each month
 * @param availableSavings money that could go towards the purchase now, outside the emergency fund
 * @param kiwiSaverBalance the KiwiSaver balance, used only for a first home
 * @param replacedHousingCost rent or board that a home would replace each month
 */
public record PurchaseImpactRequest(
        Kind kind,
        Money price,
        Money deposit,
        BigDecimal annualRate,
        Integer termMonths,
        Money upfrontCosts,
        Money ownershipCosts,
        boolean firstHome,
        LocalDate today,
        Money monthlyIncome,
        Money monthlySurplus,
        Money availableSavings,
        Money kiwiSaverBalance,
        Money replacedHousingCost) {

    public enum Kind {
        CAR,
        HOUSE,
        OTHER
    }
}
