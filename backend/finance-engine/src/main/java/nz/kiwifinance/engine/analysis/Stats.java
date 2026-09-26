package nz.kiwifinance.engine.analysis;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import nz.kiwifinance.engine.money.Money;

/**
 * Robust statistics for monthly amounts. Medians are used throughout so one unusual month does
 * not distort a person's typical figures.
 */
public final class Stats {

    private Stats() {}

    public static Money median(List<Money> values) {
        return percentile(values, 50);
    }

    /**
     * Linear-interpolation percentile, rounded to the cent.
     */
    public static Money percentile(List<Money> values, int percentile) {
        if (values.isEmpty()) {
            return Money.ZERO;
        }
        List<Money> sorted = values.stream().sorted(Comparator.naturalOrder()).toList();
        BigDecimal rank = BigDecimal.valueOf(percentile)
                .divide(BigDecimal.valueOf(100))
                .multiply(BigDecimal.valueOf(sorted.size() - 1L));
        int lower = rank.setScale(0, RoundingMode.FLOOR).intValue();
        int upper = rank.setScale(0, RoundingMode.CEILING).intValue();
        BigDecimal fraction = rank.subtract(BigDecimal.valueOf(lower));
        long low = sorted.get(lower).cents();
        long high = sorted.get(upper).cents();
        return Money.ofCents(BigDecimal.valueOf(high - low)
                .multiply(fraction)
                .add(BigDecimal.valueOf(low))
                .setScale(0, RoundingMode.HALF_UP)
                .longValueExact());
    }

    /**
     * The average month, leaving out a single month that is more than double the median.
     *
     * <p>An average reflects pay and bill cycles that a median misses: someone paid fortnightly
     * has three paydays in some months, and weekly rent lands five times in some months. Ignoring
     * one extreme month stops a bonus or a large one-off purchase from distorting the figure.
     */
    public static Money typical(List<Money> values) {
        if (values.size() < 3) {
            return mean(values);
        }
        Money median = median(values);
        Money highest = values.stream().max(Comparator.naturalOrder()).orElseThrow();
        if (median.isPositive() && highest.isGreaterThan(median.times(2))) {
            List<Money> rest = new ArrayList<>(values);
            rest.remove(highest);
            return mean(rest);
        }
        return mean(values);
    }

    public static Money mean(List<Money> values) {
        if (values.isEmpty()) {
            return Money.ZERO;
        }
        return Money.sum(values).dividedBy(values.size());
    }

    /**
     * Standard deviation divided by the mean: 0 means perfectly steady, above about 0.25 means
     * noticeably variable.
     */
    public static BigDecimal coefficientOfVariation(List<Money> values) {
        if (values.size() < 2) {
            return BigDecimal.ZERO;
        }
        BigDecimal mean = BigDecimal.valueOf(mean(values).cents());
        if (mean.signum() == 0) {
            return BigDecimal.ZERO;
        }
        BigDecimal variance = values.stream()
                .map(v -> BigDecimal.valueOf(v.cents()).subtract(mean).pow(2))
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(values.size()), MathContext.DECIMAL64);
        return variance.sqrt(MathContext.DECIMAL64).divide(mean.abs(), 4, RoundingMode.HALF_UP);
    }
}
