package nz.kiwifinance.engine.planning;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import nz.kiwifinance.engine.analysis.CategorySpending;
import nz.kiwifinance.engine.money.Money;
import nz.kiwifinance.engine.time.PayFrequency;

/**
 * Everything the affordability check needs to know about the purchase and the person's position.
 *
 * @param desiredDate when the person wants it, or {@code null} for "as soon as realistic"
 * @param liquidBalance money in everyday, savings and cash accounts
 * @param emergencyFundTarget the emergency fund to protect, which purchases never draw on
 * @param reservedForGoals money already saved towards other goals
 * @param monthlySurplus typical income minus typical spending
 * @param monthlyGoalContributions what is already promised to other goals each month
 * @param lifestyleSpending lifestyle categories, used to suggest realistic cut-backs
 * @param kiwiSaverFirstHomeAvailable KiwiSaver that could be withdrawn for a first home, or zero
 * @param financing loan details if the purchase will be partly borrowed, or {@code null}
 */
public record AffordabilityRequest(
        String itemName,
        Money price,
        LocalDate desiredDate,
        LocalDate today,
        Money liquidBalance,
        Money emergencyFundTarget,
        Money reservedForGoals,
        Money monthlySurplus,
        Money monthlyGoalContributions,
        BigDecimal savingsInterestRate,
        PayFrequency payFrequency,
        List<CategorySpending> lifestyleSpending,
        Money kiwiSaverFirstHomeAvailable,
        Financing financing,
        int monthsOfData) {

    public AffordabilityRequest {
        Objects.requireNonNull(itemName, "itemName");
        Objects.requireNonNull(price, "price");
        Objects.requireNonNull(today, "today");
        Objects.requireNonNull(payFrequency, "payFrequency");
        lifestyleSpending = List.copyOf(lifestyleSpending);
        if (!price.isPositive()) {
            throw new IllegalArgumentException("Price must be positive");
        }
    }

    public record Financing(Money deposit, BigDecimal annualRate, int termMonths, Money fees) {}
}
