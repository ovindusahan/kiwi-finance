package nz.kiwifinance.engine.analysis;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import nz.kiwifinance.engine.money.Money;

/**
 * Finds payments to the same counterparty that repeat at a steady interval for a steady amount.
 */
public final class RecurringPaymentDetector {

    private static final int MIN_OCCURRENCES = 3;
    private static final int MIN_ANNUAL_OCCURRENCES = 2;
    private static final double MIN_REGULAR_INTERVAL_SHARE = 0.75;
    private static final double MAX_AMOUNT_DEVIATION = 0.2;

    public List<RecurringPayment> detect(List<TransactionRecord> transactions, LocalDate asOf) {
        Map<String, List<TransactionRecord>> groups = transactions.stream()
                .filter(t -> t.flow() != TransactionRecord.Flow.TRANSFER
                        && !t.amount().isZero())
                .collect(Collectors.groupingBy(
                        t -> normalise(t.counterparty()) + (t.amount().isPositive() ? "+" : "-")));

        return groups.values().stream()
                .map(group -> analyse(group, asOf))
                .filter(Objects::nonNull)
                .sorted(Comparator.comparing(RecurringPayment::annualAmount).reversed())
                .toList();
    }

    private static RecurringPayment analyse(List<TransactionRecord> group, LocalDate asOf) {
        List<TransactionRecord> sorted = group.stream()
                .sorted(Comparator.comparing(TransactionRecord::date))
                .toList();
        if (sorted.size() < MIN_ANNUAL_OCCURRENCES) {
            return null;
        }
        List<Long> intervals = new ArrayList<>();
        for (int i = 1; i < sorted.size(); i++) {
            intervals.add(ChronoUnit.DAYS.between(
                    sorted.get(i - 1).date(), sorted.get(i).date()));
        }
        long medianInterval = intervals.stream().sorted().toList().get(intervals.size() / 2);
        RecurrenceInterval interval = RecurrenceInterval.closestTo(medianInterval);
        if (interval == null) {
            return null;
        }
        int required = interval == RecurrenceInterval.ANNUALLY ? MIN_ANNUAL_OCCURRENCES : MIN_OCCURRENCES;
        if (sorted.size() < required) {
            return null;
        }
        long regular = intervals.stream().filter(interval::matches).count();
        if (regular < intervals.size() * MIN_REGULAR_INTERVAL_SHARE) {
            return null;
        }

        List<Money> amounts = sorted.stream().map(t -> t.amount().abs()).toList();
        Money typical = Stats.median(amounts);
        boolean steadyAmount = amounts.stream()
                .allMatch(a -> Math.abs(a.cents() - typical.cents()) <= typical.cents() * MAX_AMOUNT_DEVIATION);
        if (!steadyAmount) {
            return null;
        }

        TransactionRecord latest = sorted.getLast();
        LocalDate nextExpected = latest.date().plusDays(interval.days());
        if (ChronoUnit.DAYS.between(latest.date(), asOf) > interval.days() * 1.5) {
            return null;
        }
        CategoryRef category = latest.category();
        return new RecurringPayment(
                displayName(latest.counterparty()),
                category,
                interval,
                typical,
                latest.amount().isPositive(),
                sorted.size(),
                latest.date(),
                nextExpected,
                isSubscription(category));
    }

    private static boolean isSubscription(CategoryRef category) {
        return category != null && category.name().toLowerCase(Locale.ROOT).contains("subscription");
    }

    static String normalise(String counterparty) {
        String cleaned = counterparty
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z ]", " ")
                .replaceAll("\\s+", " ")
                .trim();
        return Arrays.stream(cleaned.split(" "))
                .filter(word -> word.length() > 1)
                .limit(3)
                .collect(Collectors.joining(" "));
    }

    private static String displayName(String counterparty) {
        String trimmed = counterparty.trim().replaceAll("\\s+", " ");
        if (!trimmed.equals(trimmed.toUpperCase(Locale.ROOT))) {
            return trimmed;
        }
        return Arrays.stream(trimmed.toLowerCase(Locale.ROOT).split(" "))
                .map(word -> word.isEmpty() ? word : Character.toUpperCase(word.charAt(0)) + word.substring(1))
                .collect(Collectors.joining(" "));
    }
}
