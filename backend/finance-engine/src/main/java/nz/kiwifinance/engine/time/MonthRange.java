package nz.kiwifinance.engine.time;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;

/**
 * An inclusive range of calendar months.
 */
public record MonthRange(YearMonth first, YearMonth last) {

    public MonthRange {
        if (last.isBefore(first)) {
            throw new IllegalArgumentException("Range must not end before it starts");
        }
    }

    /**
     * The given number of complete months ending with the month before {@code current}.
     */
    public static MonthRange completeMonthsBefore(YearMonth current, int months) {
        if (months <= 0) {
            throw new IllegalArgumentException("Months must be positive");
        }
        return new MonthRange(current.minusMonths(months), current.minusMonths(1));
    }

    public static MonthRange endingWith(YearMonth last, int months) {
        return new MonthRange(last.minusMonths(months - 1L), last);
    }

    public LocalDate startDate() {
        return first.atDay(1);
    }

    public LocalDate endDate() {
        return last.atEndOfMonth();
    }

    public boolean contains(LocalDate date) {
        YearMonth month = YearMonth.from(date);
        return !month.isBefore(first) && !month.isAfter(last);
    }

    public int size() {
        return (int) (first.until(last, java.time.temporal.ChronoUnit.MONTHS) + 1);
    }

    public List<YearMonth> months() {
        List<YearMonth> months = new ArrayList<>(size());
        for (YearMonth month = first; !month.isAfter(last); month = month.plusMonths(1)) {
            months.add(month);
        }
        return months;
    }
}
