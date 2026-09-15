package nz.kiwifinance.engine.money;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.stream.IntStream;

/**
 * An amount of New Zealand dollars held as a whole number of cents.
 */
public record Money(long cents) implements Comparable<Money> {

    public static final Money ZERO = new Money(0);

    private static final Locale NZ = Locale.of("en", "NZ");

    public static Money ofCents(long cents) {
        return new Money(cents);
    }

    public static Money ofDollars(long dollars) {
        return new Money(Math.multiplyExact(dollars, 100));
    }

    public static Money ofDollars(BigDecimal dollars) {
        return new Money(
                dollars.setScale(2, RoundingMode.HALF_UP).movePointRight(2).longValueExact());
    }

    public static Money ofDollars(String dollars) {
        return ofDollars(new BigDecimal(dollars));
    }

    public BigDecimal toDollars() {
        return BigDecimal.valueOf(cents, 2);
    }

    public Money plus(Money other) {
        return new Money(Math.addExact(cents, other.cents));
    }

    public Money minus(Money other) {
        return new Money(Math.subtractExact(cents, other.cents));
    }

    public Money negate() {
        return new Money(Math.negateExact(cents));
    }

    public Money abs() {
        return cents < 0 ? negate() : this;
    }

    public Money times(BigDecimal factor) {
        return times(factor, RoundingMode.HALF_UP);
    }

    public Money times(BigDecimal factor, RoundingMode rounding) {
        return new Money(
                BigDecimal.valueOf(cents).multiply(factor).setScale(0, rounding).longValueExact());
    }

    public Money times(long multiplier) {
        return new Money(Math.multiplyExact(cents, multiplier));
    }

    public Money dividedBy(long divisor) {
        return dividedBy(BigDecimal.valueOf(divisor), RoundingMode.HALF_UP);
    }

    public Money dividedBy(BigDecimal divisor, RoundingMode rounding) {
        return new Money(BigDecimal.valueOf(cents).divide(divisor, 0, rounding).longValueExact());
    }

    /**
     * The ratio of this amount to another, for example a category's share of total spending.
     */
    public BigDecimal ratioOf(Money whole) {
        if (whole.isZero()) {
            return BigDecimal.ZERO;
        }
        return BigDecimal.valueOf(cents).divide(BigDecimal.valueOf(whole.cents), 6, RoundingMode.HALF_UP);
    }

    /**
     * Rounds up to the nearest multiple of the given amount, e.g. to the next $10 for budget lines.
     */
    public Money roundUpTo(Money increment) {
        if (increment.cents <= 0) {
            throw new IllegalArgumentException("Increment must be positive");
        }
        long remainder = Math.floorMod(cents, increment.cents);
        return remainder == 0 ? this : new Money(cents - remainder + increment.cents);
    }

    /**
     * Splits the amount into the given number of parts without losing cents, using
     * largest-remainder allocation: earlier parts receive the leftover cents.
     */
    public List<Money> allocate(int parts) {
        if (parts <= 0) {
            throw new IllegalArgumentException("Parts must be positive");
        }
        long base = cents / parts;
        long remainder = cents % parts;
        return IntStream.range(0, parts)
                .mapToObj(i -> new Money(base + (i < Math.abs(remainder) ? Long.signum(remainder) : 0)))
                .toList();
    }

    /**
     * Splits the amount in proportion to the given weights without losing cents.
     */
    public List<Money> allocate(long... weights) {
        long total = Arrays.stream(weights).sum();
        if (weights.length == 0 || total <= 0) {
            throw new IllegalArgumentException("Weights must sum to a positive value");
        }
        long[] shares = new long[weights.length];
        long allocated = 0;
        List<Integer> order = new ArrayList<>();
        for (int i = 0; i < weights.length; i++) {
            shares[i] = Math.multiplyExact(cents, weights[i]) / total;
            allocated += shares[i];
            order.add(i);
        }
        long[] remainders = new long[weights.length];
        for (int i = 0; i < weights.length; i++) {
            remainders[i] = Math.abs(Math.multiplyExact(cents, weights[i]) % total);
        }
        order.sort(Comparator.comparingLong((Integer i) -> remainders[i]).reversed());
        long leftover = cents - allocated;
        for (int i = 0; i < Math.abs(leftover); i++) {
            shares[order.get(i)] += Long.signum(leftover);
        }
        return Arrays.stream(shares).mapToObj(Money::new).toList();
    }

    public boolean isZero() {
        return cents == 0;
    }

    public boolean isPositive() {
        return cents > 0;
    }

    public boolean isNegative() {
        return cents < 0;
    }

    public boolean isGreaterThan(Money other) {
        return cents > other.cents;
    }

    public boolean isLessThan(Money other) {
        return cents < other.cents;
    }

    public boolean isAtLeast(Money other) {
        return cents >= other.cents;
    }

    public static Money max(Money a, Money b) {
        return a.cents >= b.cents ? a : b;
    }

    public static Money min(Money a, Money b) {
        return a.cents <= b.cents ? a : b;
    }

    public static Money sum(Iterable<Money> amounts) {
        Money total = ZERO;
        for (Money amount : amounts) {
            total = total.plus(amount);
        }
        return total;
    }

    /**
     * Formats as NZD for explanations, e.g. {@code $1,234.50}.
     */
    public String format() {
        NumberFormat format = NumberFormat.getCurrencyInstance(NZ);
        return format.format(toDollars());
    }

    /**
     * Formats as whole dollars for summaries, e.g. {@code $1,235}.
     */
    public String formatWhole() {
        NumberFormat format = NumberFormat.getCurrencyInstance(NZ);
        format.setMaximumFractionDigits(0);
        format.setMinimumFractionDigits(0);
        format.setRoundingMode(RoundingMode.HALF_UP);
        return format.format(toDollars());
    }

    @Override
    public int compareTo(Money other) {
        return Long.compare(cents, other.cents);
    }

    @Override
    public String toString() {
        return format();
    }
}
