package nz.kiwifinance.budget;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import nz.kiwifinance.analysis.AnalysisService;
import nz.kiwifinance.analysis.FinancialSnapshot;
import nz.kiwifinance.analysis.FinancialSnapshotService;
import nz.kiwifinance.category.Category;
import nz.kiwifinance.category.CategoryResponse;
import nz.kiwifinance.category.CategoryService;
import nz.kiwifinance.common.error.ApiException;
import nz.kiwifinance.common.web.MoneyResponse;
import nz.kiwifinance.engine.analysis.CategoryRef;
import nz.kiwifinance.engine.budgeting.BudgetProgress;
import nz.kiwifinance.engine.budgeting.BudgetRecommendation;
import nz.kiwifinance.engine.budgeting.BudgetRecommender;
import nz.kiwifinance.engine.budgeting.BudgetRequest;
import nz.kiwifinance.engine.budgeting.BudgetTracker;
import nz.kiwifinance.engine.explain.Explanation;
import nz.kiwifinance.engine.money.Money;
import nz.kiwifinance.goal.GoalService;
import nz.kiwifinance.transaction.TransactionService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class BudgetService {

    private static final String DEFAULT_NAME = "My budget";
    private static final UUID NO_CATEGORY = new UUID(0, 0);

    private final BudgetRepository budgets;
    private final BudgetLineRepository lines;
    private final FinancialSnapshotService snapshots;
    private final GoalService goals;
    private final CategoryService categories;
    private final TransactionService transactions;
    private final BudgetRecommender recommender = new BudgetRecommender();
    private final BudgetTracker tracker = new BudgetTracker();

    @Transactional(readOnly = true)
    public BudgetResponses.Recommendation recommend(UUID userId) {
        FinancialSnapshot snapshot = snapshots.snapshot(userId);
        BudgetRecommendation recommendation = recommender.recommend(new BudgetRequest(
                snapshot.monthlyIncome(),
                snapshot.spending(),
                snapshot.monthsOfData(),
                snapshot.profile().getTargetSavingsRate(),
                goals.monthlyCommitments(userId)));
        Map<UUID, Category> categoryById = categories.byId(userId);
        return new BudgetResponses.Recommendation(
                MoneyResponse.of(recommendation.monthlyIncome()),
                recommendation.lines().stream()
                        .map(line -> new BudgetResponses.RecommendedLine(
                                AnalysisService.category(line.category(), categoryById),
                                line.kind(),
                                MoneyResponse.of(line.typical()),
                                MoneyResponse.of(line.recommended()),
                                line.isTrimmed(),
                                line.rationale()))
                        .toList(),
                MoneyResponse.of(recommendation.plannedSpending()),
                MoneyResponse.of(recommendation.plannedSavings()),
                recommendation.savingsRate(),
                MoneyResponse.of(recommendation.targetSavings()),
                recommendation.meetsTarget(),
                new Explanation(
                        recommendation.explanation().summary(),
                        recommendation.explanation().steps(),
                        concat(recommendation.explanation().assumptions(), snapshot.assumptions())));
    }

    @Transactional(readOnly = true)
    public Optional<BudgetResponses.Current> current(UUID userId, YearMonth month) {
        return budgets.findByUserIdAndActiveTrue(userId).map(budget -> view(budget, month));
    }

    /**
     * Saves a new active budget, replacing any existing one.
     */
    @Transactional
    BudgetResponses.Current create(UUID userId, BudgetRequests.Save request) {
        budgets.findByUserIdAndActiveTrue(userId).ifPresent(existing -> {
            existing.deactivate();
            budgets.saveAndFlush(existing);
        });
        Budget budget = budgets.save(new Budget(userId, name(request)));
        saveLines(userId, budget, request.lines());
        return view(budget, null);
    }

    @Transactional
    BudgetResponses.Current update(UUID userId, UUID budgetId, BudgetRequests.Save request) {
        Budget budget = budgets.findByIdAndUserId(budgetId, userId).orElseThrow(() -> ApiException.notFound("Budget"));
        budget.rename(name(request));
        lines.deleteByBudgetId(budgetId);
        saveLines(userId, budget, request.lines());
        return view(budget, null);
    }

    @Transactional
    void delete(UUID userId, UUID budgetId) {
        Budget budget = budgets.findByIdAndUserId(budgetId, userId).orElseThrow(() -> ApiException.notFound("Budget"));
        budgets.delete(budget);
    }

    /**
     * How the active budget went in the last complete month, for scores and achievements.
     */
    @Transactional(readOnly = true)
    public Optional<BudgetResponses.Progress> lastMonth(UUID userId, LocalDate today) {
        return current(userId, YearMonth.from(today).minusMonths(1)).map(BudgetResponses.Current::progress);
    }

    /**
     * The monthly allowance for lifestyle and uncategorised spending in the active budget.
     */
    @Transactional(readOnly = true)
    public Optional<Money> lifestyleAllowance(UUID userId) {
        Map<UUID, Category> categoryById = categories.byId(userId);
        return budgets.findByUserIdAndActiveTrue(userId)
                .map(budget -> Money.ofCents(lines.findByBudgetId(budget.getId()).stream()
                        .filter(line -> line.getCategoryId() == null
                                || Optional.ofNullable(categoryById.get(line.getCategoryId()))
                                        .map(c -> !c.getGroup().isEssential())
                                        .orElse(false))
                        .mapToLong(BudgetLine::getLimitCents)
                        .sum()));
    }

    private void saveLines(UUID userId, Budget budget, List<BudgetRequests.Line> requested) {
        Map<UUID, BudgetRequests.Line> byCategory = requested.stream()
                .collect(Collectors.toMap(
                        line -> line.categoryId() == null ? NO_CATEGORY : line.categoryId(),
                        Function.identity(),
                        (first, second) -> second));
        byCategory.values().forEach(line -> {
            if (line.categoryId() != null) {
                categories.get(userId, line.categoryId());
            }
            lines.save(new BudgetLine(budget.getId(), line.categoryId(), line.limitCents(), line.rationale()));
        });
    }

    private BudgetResponses.Current view(Budget budget, YearMonth month) {
        UUID userId = budget.getUserId();
        Map<UUID, Category> categoryById = categories.byId(userId);
        List<BudgetLine> budgetLines = lines.findByBudgetId(budget.getId());
        FinancialSnapshot snapshot = snapshots.snapshot(userId);
        YearMonth target = month == null ? snapshot.currentMonth() : month;
        LocalDate asOf = target.equals(snapshot.currentMonth()) ? snapshot.today() : target.atEndOfMonth();

        List<BudgetTracker.Limit> limits = budgetLines.stream()
                .map(line -> new BudgetTracker.Limit(
                        line.getCategoryId() == null
                                ? null
                                : Optional.ofNullable(categoryById.get(line.getCategoryId()))
                                        .map(Category::toRef)
                                        .orElse(null),
                        Money.ofCents(line.getLimitCents())))
                .toList();
        BudgetProgress progress =
                tracker.progress(limits, transactions.records(userId, target.atDay(1), target.atEndOfMonth()), asOf);
        Map<String, String> rationaleByKey = budgetLines.stream()
                .collect(Collectors.toMap(
                        line -> line.getCategoryId() == null
                                ? ""
                                : line.getCategoryId().toString(),
                        line -> line.getRationale() == null ? "" : line.getRationale(),
                        (a, b) -> a));

        List<BudgetResponses.Line> lineViews = progress.lines().stream()
                .map(line -> {
                    String key = line.category() == null ? "" : line.category().id();
                    CategoryRef ref = line.category();
                    CategoryResponse category =
                            ref == null ? null : CategoryResponse.from(categoryById.get(UUID.fromString(ref.id())));
                    return new BudgetResponses.Line(
                            category,
                            MoneyResponse.of(line.limit()),
                            MoneyResponse.of(line.spent()),
                            MoneyResponse.of(line.remaining()),
                            line.usedFraction(),
                            line.status(),
                            rationaleByKey.getOrDefault(key, null));
                })
                .sorted((a, b) -> Long.compare(b.limit().cents(), a.limit().cents()))
                .toList();
        var progressView = new BudgetResponses.Progress(
                progress.month(),
                progress.dayOfMonth(),
                progress.daysInMonth(),
                MoneyResponse.of(progress.totalLimit()),
                MoneyResponse.of(progress.totalSpent()),
                MoneyResponse.of(progress.totalRemaining()),
                (int) progress.lines().stream()
                        .filter(l -> l.status() == BudgetProgress.Status.OVER)
                        .count(),
                (int) progress.lines().stream()
                        .filter(l -> l.status() == BudgetProgress.Status.AT_RISK)
                        .count(),
                lineViews);
        return new BudgetResponses.Current(
                budget.getId(), budget.getName(), MoneyResponse.of(progress.totalLimit()), progressView);
    }

    private static String name(BudgetRequests.Save request) {
        return request.name() == null || request.name().isBlank()
                ? DEFAULT_NAME
                : request.name().trim();
    }

    private static <T> List<T> concat(List<T> first, List<T> second) {
        return Stream.concat(first.stream(), second.stream()).toList();
    }
}
