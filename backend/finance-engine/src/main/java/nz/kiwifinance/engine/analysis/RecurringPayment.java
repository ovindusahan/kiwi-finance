package nz.kiwifinance.engine.analysis;

import java.time.LocalDate;
import nz.kiwifinance.engine.money.Money;

/**
 * A payment that repeats on a regular schedule, such as rent, a phone plan or a streaming
 * subscription. Incoming payments such as wages are detected too.
 *
 * @param typicalAmount the usual size of each payment, always positive
 */
public record RecurringPayment(
        String name,
        CategoryRef category,
        RecurrenceInterval interval,
        Money typicalAmount,
        boolean incoming,
        int occurrences,
        LocalDate lastDate,
        LocalDate nextExpectedDate,
        boolean subscription) {

    public Money annualAmount() {
        return typicalAmount.times(interval.perYear());
    }

    public Money monthlyAmount() {
        return annualAmount().dividedBy(12);
    }
}
