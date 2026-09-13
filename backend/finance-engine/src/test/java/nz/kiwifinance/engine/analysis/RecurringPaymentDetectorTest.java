package nz.kiwifinance.engine.analysis;

import static nz.kiwifinance.engine.analysis.Fixtures.GROCERIES;
import static nz.kiwifinance.engine.analysis.Fixtures.SUBSCRIPTIONS;
import static nz.kiwifinance.engine.analysis.Fixtures.earn;
import static nz.kiwifinance.engine.analysis.Fixtures.spend;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import nz.kiwifinance.engine.money.Money;
import org.junit.jupiter.api.Test;

class RecurringPaymentDetectorTest {

    private final RecurringPaymentDetector detector = new RecurringPaymentDetector();
    private final LocalDate asOf = LocalDate.of(2026, 10, 1);

    @Test
    void detectsMonthlySubscriptionsAndFortnightlyPay() {
        List<TransactionRecord> transactions = new ArrayList<>();
        for (int month = 5; month <= 9; month++) {
            transactions.add(spend(LocalDate.of(2026, month, 15), "22.99", SUBSCRIPTIONS, "NETFLIX.COM"));
        }
        for (LocalDate payday = LocalDate.of(2026, 6, 4); payday.isBefore(asOf); payday = payday.plusDays(14)) {
            transactions.add(earn(payday, "2400"));
        }

        var result = detector.detect(transactions, asOf);

        assertThat(result).hasSize(2);
        RecurringPayment pay = result.getFirst();
        assertThat(pay.incoming()).isTrue();
        assertThat(pay.interval()).isEqualTo(RecurrenceInterval.FORTNIGHTLY);
        assertThat(pay.annualAmount()).isEqualTo(Money.ofDollars(62_400));

        RecurringPayment netflix = result.get(1);
        assertThat(netflix.name()).isEqualTo("Netflix.com");
        assertThat(netflix.interval()).isEqualTo(RecurrenceInterval.MONTHLY);
        assertThat(netflix.subscription()).isTrue();
        assertThat(netflix.nextExpectedDate()).isEqualTo(LocalDate.of(2026, 10, 15));
    }

    @Test
    void ignoresIrregularPaymentsAndLapsedSubscriptions() {
        List<TransactionRecord> transactions = List.of(
                spend(LocalDate.of(2026, 8, 1), "180", GROCERIES, "Countdown"),
                spend(LocalDate.of(2026, 8, 4), "35", GROCERIES, "Countdown"),
                spend(LocalDate.of(2026, 8, 20), "260", GROCERIES, "Countdown"),
                spend(LocalDate.of(2026, 3, 1), "15", SUBSCRIPTIONS, "Old App"),
                spend(LocalDate.of(2026, 4, 1), "15", SUBSCRIPTIONS, "Old App"),
                spend(LocalDate.of(2026, 5, 1), "15", SUBSCRIPTIONS, "Old App"));

        assertThat(detector.detect(transactions, asOf)).isEmpty();
    }

    @Test
    void normalisesBankReferencesIntoTheSameCounterparty() {
        assertThat(RecurringPaymentDetector.normalise("SPARK NZ 4492 AUCKLAND"))
                .isEqualTo(RecurringPaymentDetector.normalise("Spark NZ 1033 Auckland"));
    }
}
