package nz.kiwifinance.engine.progress;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import nz.kiwifinance.engine.analysis.CashflowAnalysis;
import nz.kiwifinance.engine.analysis.MonthSummary;
import nz.kiwifinance.engine.analysis.TransactionRecord;
import nz.kiwifinance.engine.money.Money;

public final class StreakCalculator {

    private static final int MAX_DAYS = 366;
    private static final int MAX_WEEKS = 104;

    /**
     * @param monthlyLifestyleBudget the budgeted lifestyle spending per month, or {@code null}
     */
    public Streaks calculate(
            List<TransactionRecord> transactions,
            CashflowAnalysis cashflow,
            Money monthlyLifestyleBudget,
            LocalDate today) {
        Set<LocalDate> spendDays = transactions.stream()
                .filter(StreakCalculator::isLifestyleSpend)
                .map(TransactionRecord::date)
                .collect(Collectors.toSet());
        LocalDate earliest = transactions.stream()
                .map(TransactionRecord::date)
                .min(LocalDate::compareTo)
                .orElse(today);

        int noSpend = 0;
        for (LocalDate day = today.minusDays(1);
                !day.isBefore(earliest) && noSpend < MAX_DAYS;
                day = day.minusDays(1)) {
            if (spendDays.contains(day)) {
                break;
            }
            noSpend++;
        }

        int thisMonth = 0;
        for (LocalDate day = today.withDayOfMonth(1); day.isBefore(today); day = day.plusDays(1)) {
            if (!day.isBefore(earliest) && !spendDays.contains(day)) {
                thisMonth++;
            }
        }

        int surplusMonths = 0;
        List<MonthSummary> months = cashflow.months();
        for (int i = months.size() - 1; i >= 0; i--) {
            MonthSummary month = months.get(i);
            if (!month.hasActivity() || !month.net().isPositive()) {
                break;
            }
            surplusMonths++;
        }

        Integer underBudgetWeeks = monthlyLifestyleBudget == null
                ? null
                : underBudgetWeeks(transactions, monthlyLifestyleBudget, today, earliest);
        return new Streaks(noSpend, thisMonth, surplusMonths, underBudgetWeeks);
    }

    private static int underBudgetWeeks(
            List<TransactionRecord> transactions, Money monthlyBudget, LocalDate today, LocalDate earliest) {
        Money weeklyBudget = monthlyBudget.times(12).dividedBy(BigDecimal.valueOf(52), RoundingMode.HALF_UP);
        Map<LocalDate, Money> byWeek = transactions.stream()
                .filter(StreakCalculator::isLifestyleSpend)
                .collect(Collectors.groupingBy(
                        t -> t.date().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)),
                        Collectors.reducing(Money.ZERO, t -> t.amount().negate(), Money::plus)));
        LocalDate weekStart =
                today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).minusWeeks(1);
        int streak = 0;
        while (streak < MAX_WEEKS && !weekStart.plusDays(6).isBefore(earliest)) {
            if (byWeek.getOrDefault(weekStart, Money.ZERO).isGreaterThan(weeklyBudget)) {
                break;
            }
            streak++;
            weekStart = weekStart.minusWeeks(1);
        }
        return streak;
    }

    private static boolean isLifestyleSpend(TransactionRecord transaction) {
        return transaction.flow() == TransactionRecord.Flow.SPENDING
                && transaction.amount().isNegative()
                && (transaction.category() == null
                        || !transaction.category().group().isEssential());
    }
}
