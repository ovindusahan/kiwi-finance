package nz.kiwifinance.engine.time;

import java.time.LocalDate;
import java.time.Month;

/**
 * A New Zealand tax year, running from 1 April to 31 March. Identified by the calendar year in
 * which it starts, so 2026 is the 2026/27 tax year (which Inland Revenue calls the 2027 tax year).
 */
public record TaxYear(int startYear) implements Comparable<TaxYear> {

    public static TaxYear containing(LocalDate date) {
        return new TaxYear(date.getMonthValue() >= Month.APRIL.getValue() ? date.getYear() : date.getYear() - 1);
    }

    public LocalDate firstDay() {
        return LocalDate.of(startYear, Month.APRIL, 1);
    }

    public LocalDate lastDay() {
        return LocalDate.of(startYear + 1, Month.MARCH, 31);
    }

    public boolean contains(LocalDate date) {
        return !date.isBefore(firstDay()) && !date.isAfter(lastDay());
    }

    public TaxYear next() {
        return new TaxYear(startYear + 1);
    }

    public String label() {
        return "%d/%02d".formatted(startYear, (startYear + 1) % 100);
    }

    @Override
    public int compareTo(TaxYear other) {
        return Integer.compare(startYear, other.startYear);
    }

    @Override
    public String toString() {
        return label();
    }
}
