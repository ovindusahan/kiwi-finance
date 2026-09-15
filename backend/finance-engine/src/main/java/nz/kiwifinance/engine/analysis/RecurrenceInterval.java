package nz.kiwifinance.engine.analysis;

public enum RecurrenceInterval {
    WEEKLY(7, 2, 52),
    FORTNIGHTLY(14, 3, 26),
    MONTHLY(30, 5, 12),
    QUARTERLY(91, 10, 4),
    ANNUALLY(365, 20, 1);

    private final int days;
    private final int toleranceDays;
    private final int perYear;

    RecurrenceInterval(int days, int toleranceDays, int perYear) {
        this.days = days;
        this.toleranceDays = toleranceDays;
        this.perYear = perYear;
    }

    public int days() {
        return days;
    }

    public int perYear() {
        return perYear;
    }

    public boolean matches(long intervalDays) {
        return Math.abs(intervalDays - days) <= toleranceDays;
    }

    static RecurrenceInterval closestTo(long intervalDays) {
        for (RecurrenceInterval interval : values()) {
            if (interval.matches(intervalDays)) {
                return interval;
            }
        }
        return null;
    }
}
