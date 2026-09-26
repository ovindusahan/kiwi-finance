package nz.kiwifinance.engine.budgeting;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import nz.kiwifinance.engine.analysis.CategoryRef;
import nz.kiwifinance.engine.analysis.TransactionRecord;
import nz.kiwifinance.engine.money.Money;

public final class BudgetTracker {

    /**
     * Spending running ahead of the month by more than this margin is flagged as at risk.
     */
    private static final BigDecimal PACE_TOLERANCE = new BigDecimal("1.15");

    public record Limit(CategoryRef category, Money amount) {}

    public BudgetProgress progress(List<Limit> limits, List<TransactionRecord> transactions, LocalDate today) {
        YearMonth month = YearMonth.from(today);
        Map<String, Money> spentByCategory = transactions.stream()
                .filter(t -> YearMonth.from(t.date()).equals(month) && t.flow() == TransactionRecord.Flow.SPENDING)
                .collect(Collectors.groupingBy(
                        t -> Optional.ofNullable(t.category())
                                .map(CategoryRef::id)
                                .orElse(""),
                        Collectors.reducing(Money.ZERO, t -> t.amount().negate(), Money::plus)));

        BigDecimal elapsed = BigDecimal.valueOf(today.getDayOfMonth())
                .divide(BigDecimal.valueOf(month.lengthOfMonth()), 4, RoundingMode.HALF_UP);

        List<BudgetProgress.LineProgress> lines = limits.stream()
                .map(limit -> {
                    String key =
                            limit.category() == null ? "" : limit.category().id();
                    Money spent = Money.max(Money.ZERO, spentByCategory.getOrDefault(key, Money.ZERO));
                    return line(limit, spent, elapsed);
                })
                .toList();

        return new BudgetProgress(
                month,
                today.getDayOfMonth(),
                month.lengthOfMonth(),
                lines,
                Money.sum(limits.stream().map(Limit::amount).toList()),
                Money.sum(lines.stream().map(BudgetProgress.LineProgress::spent).toList()));
    }

    private static BudgetProgress.LineProgress line(Limit limit, Money spent, BigDecimal elapsed) {
        Money expectedSoFar = limit.amount().times(elapsed);
        BudgetProgress.Status status;
        if (spent.isGreaterThan(limit.amount())) {
            status = BudgetProgress.Status.OVER;
        } else if (spent.isPositive() && spent.isGreaterThan(expectedSoFar.times(PACE_TOLERANCE))) {
            status = BudgetProgress.Status.AT_RISK;
        } else {
            status = BudgetProgress.Status.ON_TRACK;
        }
        return new BudgetProgress.LineProgress(
                limit.category(),
                limit.amount(),
                spent,
                limit.amount().minus(spent),
                spent.ratioOf(limit.amount()).setScale(4, RoundingMode.HALF_UP),
                status);
    }
}
