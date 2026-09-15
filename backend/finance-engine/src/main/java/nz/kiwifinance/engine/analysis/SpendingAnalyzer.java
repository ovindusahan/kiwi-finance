package nz.kiwifinance.engine.analysis;

import java.time.YearMonth;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import nz.kiwifinance.engine.money.Money;
import nz.kiwifinance.engine.time.MonthRange;

public final class SpendingAnalyzer {

    private static final String UNCATEGORISED = "";

    /**
     * Spending per category over the range, largest first. Medians and quartiles are taken over
     * every month in the range, so a category used only occasionally has a low typical amount.
     */
    public List<CategorySpending> byCategory(List<TransactionRecord> transactions, MonthRange range) {
        Map<String, CategoryRef> categories = new LinkedHashMap<>();
        Map<String, Map<YearMonth, Money>> totals = new LinkedHashMap<>();
        Money overall = Money.ZERO;

        for (TransactionRecord transaction : transactions) {
            if (!range.contains(transaction.date()) || transaction.flow() != TransactionRecord.Flow.SPENDING) {
                continue;
            }
            String key = Optional.ofNullable(transaction.category())
                    .map(CategoryRef::id)
                    .orElse(UNCATEGORISED);
            if (transaction.category() != null) {
                categories.putIfAbsent(key, transaction.category());
            }
            Money spent = transaction.amount().negate();
            totals.computeIfAbsent(key, k -> new LinkedHashMap<>())
                    .merge(YearMonth.from(transaction.date()), spent, Money::plus);
            overall = overall.plus(spent);
        }

        Money grandTotal = overall;
        return totals.entrySet().stream()
                .map(entry -> toCategorySpending(categories.get(entry.getKey()), entry.getValue(), range, grandTotal))
                .filter(spending -> spending.total().isPositive())
                .sorted(Comparator.comparing(CategorySpending::total).reversed())
                .collect(Collectors.toList());
    }

    private static CategorySpending toCategorySpending(
            CategoryRef category, Map<YearMonth, Money> byMonth, MonthRange range, Money grandTotal) {
        List<CategorySpending.MonthAmount> monthly = range.months().stream()
                .map(month -> new CategorySpending.MonthAmount(month, byMonth.getOrDefault(month, Money.ZERO)))
                .toList();
        List<Money> amounts =
                monthly.stream().map(CategorySpending.MonthAmount::amount).toList();
        Money total = Money.sum(amounts);
        return new CategorySpending(
                category,
                total,
                total.ratioOf(grandTotal),
                Stats.median(amounts),
                Stats.percentile(amounts, 25),
                monthly);
    }
}
