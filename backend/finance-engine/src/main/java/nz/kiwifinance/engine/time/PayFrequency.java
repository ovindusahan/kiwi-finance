package nz.kiwifinance.engine.time;

import java.math.BigDecimal;
import java.math.RoundingMode;
import nz.kiwifinance.engine.money.Money;

public enum PayFrequency {
    WEEKLY(52, "week"),
    FORTNIGHTLY(26, "fortnight"),
    FOUR_WEEKLY(13, "four weeks"),
    MONTHLY(12, "month"),
    ANNUALLY(1, "year");

    private final int periodsPerYear;
    private final String periodName;

    PayFrequency(int periodsPerYear, String periodName) {
        this.periodsPerYear = periodsPerYear;
        this.periodName = periodName;
    }

    public int periodsPerYear() {
        return periodsPerYear;
    }

    /**
     * The noun used in plain-language amounts, as in "$45 a week".
     */
    public String periodName() {
        return periodName;
    }

    public Money toAnnual(Money perPeriod) {
        return perPeriod.times(periodsPerYear);
    }

    public Money fromAnnual(Money annual) {
        return annual.dividedBy(periodsPerYear);
    }

    /**
     * Converts through the annual amount so that, for example, $100 a week becomes $433.33 a month.
     */
    public Money convert(Money perPeriod, PayFrequency target) {
        if (target == this) {
            return perPeriod;
        }
        return perPeriod
                .times(periodsPerYear)
                .dividedBy(BigDecimal.valueOf(target.periodsPerYear), RoundingMode.HALF_UP);
    }
}
