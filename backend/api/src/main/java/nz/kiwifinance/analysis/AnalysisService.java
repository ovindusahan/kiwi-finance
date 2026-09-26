package nz.kiwifinance.analysis;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import nz.kiwifinance.category.Category;
import nz.kiwifinance.category.CategoryResponse;
import nz.kiwifinance.category.CategoryService;
import nz.kiwifinance.common.web.MoneyResponse;
import nz.kiwifinance.engine.analysis.CashflowAnalysis;
import nz.kiwifinance.engine.analysis.CashflowAnalyzer;
import nz.kiwifinance.engine.analysis.CategoryRef;
import nz.kiwifinance.engine.analysis.CategorySpending;
import nz.kiwifinance.engine.analysis.RecurringPayment;
import nz.kiwifinance.engine.analysis.SpendingAnalyzer;
import nz.kiwifinance.engine.analysis.TransactionRecord;
import nz.kiwifinance.engine.money.Money;
import nz.kiwifinance.engine.time.MonthRange;
import nz.kiwifinance.engine.time.NzTime;
import nz.kiwifinance.transaction.TransactionService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AnalysisService {

    private static final int MAX_MONTHS = 24;
    private static final int UPCOMING_DAYS = 30;

    private final TransactionService transactions;
    private final CategoryService categories;
    private final FinancialSnapshotService snapshots;
    private final Clock clock;
    private final CashflowAnalyzer cashflowAnalyzer = new CashflowAnalyzer();
    private final SpendingAnalyzer spendingAnalyzer = new SpendingAnalyzer();

    /**
     * Month-by-month cash flow, ending with the current month so far.
     */
    @Transactional(readOnly = true)
    public AnalysisResponses.Cashflow cashflow(UUID userId, int months) {
        int count = Math.clamp(months, 1, MAX_MONTHS);
        LocalDate today = NzTime.today(clock);
        YearMonth current = YearMonth.from(today);
        MonthRange range = MonthRange.endingWith(current, count);
        List<TransactionRecord> records = transactions.records(userId, range.startDate(), today);
        CashflowAnalysis analysis = cashflowAnalyzer.analyse(records, range);
        List<AnalysisResponses.Month> monthViews = analysis.months().stream()
                .map(month -> AnalysisResponses.Month.from(month, month.month().isBefore(current)))
                .toList();
        return new AnalysisResponses.Cashflow(monthViews, typical(snapshots.snapshot(userId)));
    }

    /**
     * Spending by category over the last complete months, compared with the same length of time
     * before that.
     */
    @Transactional(readOnly = true)
    public AnalysisResponses.Spending spending(UUID userId, int months) {
        int count = Math.clamp(months, 1, 12);
        LocalDate today = NzTime.today(clock);
        MonthRange range = MonthRange.completeMonthsBefore(YearMonth.from(today), count);
        MonthRange previous =
                new MonthRange(range.first().minusMonths(count), range.first().minusMonths(1));
        List<TransactionRecord> records = transactions.records(userId, previous.startDate(), range.endDate());

        List<CategorySpending> currentSpending = spendingAnalyzer.byCategory(records, range);
        Map<String, Money> previousTotals = spendingAnalyzer.byCategory(records, previous).stream()
                .collect(Collectors.toMap(AnalysisService::key, CategorySpending::total));
        Map<UUID, Category> categoryById = categories.byId(userId);

        List<AnalysisResponses.CategorySpend> views = currentSpending.stream()
                .map(spend -> {
                    Money before = previousTotals.getOrDefault(key(spend), Money.ZERO);
                    BigDecimal change = before.isPositive()
                            ? spend.total().minus(before).ratioOf(before).setScale(4, RoundingMode.HALF_UP)
                            : null;
                    return new AnalysisResponses.CategorySpend(
                            category(spend.category(), categoryById),
                            MoneyResponse.of(spend.total()),
                            spend.share(),
                            MoneyResponse.of(spend.monthlyMedian()),
                            MoneyResponse.of(before),
                            change,
                            spend.monthly().stream()
                                    .map(m ->
                                            new AnalysisResponses.MonthAmount(m.month(), MoneyResponse.of(m.amount())))
                                    .toList());
                })
                .toList();
        return new AnalysisResponses.Spending(
                range.startDate(),
                range.endDate(),
                MoneyResponse.of(Money.sum(
                        currentSpending.stream().map(CategorySpending::total).toList())),
                MoneyResponse.of(Money.sum(previousTotals.values())),
                views);
    }

    @Transactional(readOnly = true)
    public AnalysisResponses.RecurringSummary recurring(UUID userId) {
        FinancialSnapshot snapshot = snapshots.snapshot(userId);
        Map<UUID, Category> categoryById = categories.byId(userId);
        List<AnalysisResponses.Recurring> payments = snapshot.recurring().stream()
                .map(payment -> recurring(payment, categoryById))
                .toList();
        LocalDate horizon = snapshot.today().plusDays(UPCOMING_DAYS);
        List<AnalysisResponses.Recurring> upcoming = payments.stream()
                .filter(p -> !p.incoming() && !p.nextExpectedDate().isAfter(horizon))
                .sorted(Comparator.comparing(AnalysisResponses.Recurring::nextExpectedDate))
                .toList();
        Money bills = Money.sum(snapshot.recurring().stream()
                .filter(p -> !p.incoming())
                .map(RecurringPayment::monthlyAmount)
                .toList());
        Money subscriptions = Money.sum(snapshot.recurring().stream()
                .filter(p -> !p.incoming() && p.subscription())
                .map(RecurringPayment::monthlyAmount)
                .toList());
        return new AnalysisResponses.RecurringSummary(
                payments, upcoming, MoneyResponse.of(bills), MoneyResponse.of(subscriptions));
    }

    public AnalysisResponses.Typical typical(FinancialSnapshot snapshot) {
        CashflowAnalysis cashflow = snapshot.cashflow();
        Money income = snapshot.monthlyIncome();
        BigDecimal savingsRate = income.isPositive()
                ? snapshot.monthlySurplus().ratioOf(income).setScale(4, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;
        return new AnalysisResponses.Typical(
                MoneyResponse.of(income),
                snapshot.incomeBasis(),
                MoneyResponse.of(cashflow.typicalSpending()),
                MoneyResponse.of(cashflow.typicalEssentialSpending()),
                MoneyResponse.of(cashflow.typicalLifestyleSpending()),
                MoneyResponse.of(snapshot.monthlySurplus()),
                savingsRate,
                cashflow.hasVariableIncome(),
                cashflow.monthsOfData());
    }

    public static CategoryResponse category(CategoryRef ref, Map<UUID, Category> categoryById) {
        return Optional.ofNullable(ref)
                .map(r -> categoryById.get(UUID.fromString(r.id())))
                .map(CategoryResponse::from)
                .orElse(null);
    }

    public static AnalysisResponses.Recurring recurring(RecurringPayment payment, Map<UUID, Category> categoryById) {
        return new AnalysisResponses.Recurring(
                payment.name(),
                category(payment.category(), categoryById),
                payment.interval(),
                MoneyResponse.of(payment.typicalAmount()),
                payment.incoming(),
                payment.occurrences(),
                payment.lastDate(),
                payment.nextExpectedDate(),
                MoneyResponse.of(payment.monthlyAmount()),
                MoneyResponse.of(payment.annualAmount()),
                payment.subscription());
    }

    private static String key(CategorySpending spending) {
        return spending.isUncategorised() ? "" : spending.category().id();
    }
}
