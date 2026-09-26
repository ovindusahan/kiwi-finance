package nz.kiwifinance.engine.planning;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.OptionalInt;
import nz.kiwifinance.engine.money.Money;

/**
 * Projects savings that grow by regular monthly contributions plus interest compounded monthly.
 * The rate is the after-tax annual rate the person can expect on their savings.
 */
public final class SavingsProjector {

    public static final int MAX_MONTHS = 600;

    private final BigDecimal monthlyRate;

    public SavingsProjector(BigDecimal annualRate) {
        if (annualRate.signum() < 0) {
            throw new IllegalArgumentException("Interest rate cannot be negative");
        }
        this.monthlyRate = annualRate.divide(BigDecimal.valueOf(12), MathContext.DECIMAL64);
    }

    public Money futureValue(Money start, Money monthlyContribution, int months) {
        BigDecimal balance = BigDecimal.valueOf(start.cents());
        BigDecimal contribution = BigDecimal.valueOf(monthlyContribution.cents());
        for (int month = 0; month < months; month++) {
            balance = balance.add(balance.multiply(monthlyRate)).add(contribution);
        }
        return Money.ofCents(balance.setScale(0, RoundingMode.HALF_UP).longValueExact());
    }

    /**
     * Whole months until the balance reaches the target, or empty if it never does within
     * {@link #MAX_MONTHS}.
     */
    public OptionalInt monthsToReach(Money target, Money start, Money monthlyContribution) {
        if (start.isAtLeast(target)) {
            return OptionalInt.of(0);
        }
        if (!monthlyContribution.isPositive() && (monthlyRate.signum() == 0 || !start.isPositive())) {
            return OptionalInt.empty();
        }
        BigDecimal goal = BigDecimal.valueOf(target.cents());
        BigDecimal balance = BigDecimal.valueOf(start.cents());
        BigDecimal contribution = BigDecimal.valueOf(monthlyContribution.cents());
        for (int month = 1; month <= MAX_MONTHS; month++) {
            balance = balance.add(balance.multiply(monthlyRate)).add(contribution);
            if (balance.compareTo(goal) >= 0) {
                return OptionalInt.of(month);
            }
        }
        return OptionalInt.empty();
    }

    /**
     * The monthly contribution, rounded up to the cent, that reaches the target in the given number
     * of months.
     */
    public Money requiredMonthlyContribution(Money target, Money start, int months) {
        if (start.isAtLeast(target)) {
            return Money.ZERO;
        }
        if (months <= 0) {
            return target.minus(start);
        }
        BigDecimal goal = BigDecimal.valueOf(target.cents());
        BigDecimal initial = BigDecimal.valueOf(start.cents());
        if (monthlyRate.signum() == 0) {
            return Money.ofCents(goal.subtract(initial)
                    .divide(BigDecimal.valueOf(months), 0, RoundingMode.CEILING)
                    .longValueExact());
        }
        BigDecimal growth = BigDecimal.ONE.add(monthlyRate).pow(months, MathContext.DECIMAL64);
        BigDecimal needed = goal.subtract(initial.multiply(growth));
        if (needed.signum() <= 0) {
            return Money.ZERO;
        }
        BigDecimal payment =
                needed.multiply(monthlyRate).divide(growth.subtract(BigDecimal.ONE), MathContext.DECIMAL64);
        return Money.ofCents(payment.setScale(0, RoundingMode.CEILING).longValueExact());
    }
}
