package nz.kiwifinance.dashboard;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import nz.kiwifinance.account.Account;
import nz.kiwifinance.analysis.AnalysisResponses;
import nz.kiwifinance.analysis.AnalysisService;
import nz.kiwifinance.analysis.FinancialSnapshot;
import nz.kiwifinance.analysis.FinancialSnapshotService;
import nz.kiwifinance.analysis.IncomeBasis;
import nz.kiwifinance.bankfeed.BankConnectionService;
import nz.kiwifinance.bankfeed.BankFeedProvider;
import nz.kiwifinance.budget.BudgetResponses;
import nz.kiwifinance.budget.BudgetService;
import nz.kiwifinance.category.Category;
import nz.kiwifinance.category.CategoryService;
import nz.kiwifinance.common.web.MoneyResponse;
import nz.kiwifinance.emergencyfund.EmergencyFundService;
import nz.kiwifinance.engine.analysis.CategorySpending;
import nz.kiwifinance.engine.analysis.SpendingAnalyzer;
import nz.kiwifinance.engine.emergencyfund.EmergencyFundPlan;
import nz.kiwifinance.engine.insights.InsightContext;
import nz.kiwifinance.engine.insights.InsightGenerator;
import nz.kiwifinance.engine.insights.InsightRequest;
import nz.kiwifinance.engine.money.Money;
import nz.kiwifinance.engine.time.MonthRange;
import nz.kiwifinance.goal.GoalPlanService;
import nz.kiwifinance.goal.GoalResponses;
import nz.kiwifinance.goal.GoalService;
import nz.kiwifinance.goal.GoalStatus;
import nz.kiwifinance.preferences.PreferencesService;
import nz.kiwifinance.progress.ProgressResponse;
import nz.kiwifinance.progress.ProgressService;
import nz.kiwifinance.transaction.TransactionService;
import nz.kiwifinance.user.User;
import nz.kiwifinance.user.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
class DashboardService {

    private static final int UPCOMING_DAYS = 14;
    private static final int MAX_GOALS = 3;
    private static final int MAX_INSIGHTS = 4;

    private final FinancialSnapshotService snapshots;
    private final AnalysisService analysis;
    private final EmergencyFundService emergencyFund;
    private final BudgetService budgets;
    private final GoalService goals;
    private final ProgressService progress;
    private final BankConnectionService bankConnections;
    private final CategoryService categories;
    private final UserRepository users;
    private final PreferencesService preferences;
    private final GoalPlanService goalPlans;
    private final TransactionService transactions;
    private final InsightGenerator insightGenerator = new InsightGenerator();
    private final SpendingAnalyzer spendingAnalyzer = new SpendingAnalyzer();

    @Transactional(readOnly = true)
    DashboardResponse dashboard(UUID userId) {
        FinancialSnapshot snapshot = snapshots.snapshot(userId);
        EmergencyFundPlan fund = emergencyFund.plan(snapshot);
        ProgressResponse progressView = progress.forSnapshot(snapshot);
        var budget = budgets.current(userId, null);
        List<GoalResponses.View> goalViews = goals.list(userId);
        Map<UUID, Category> categoryById = categories.byId(userId);
        LocalDate horizon = snapshot.today().plusDays(UPCOMING_DAYS);
        boolean hasBankFeed =
                bankConnections.current(userId, BankFeedProvider.AKAHU).isPresent();

        List<AnalysisResponses.Recurring> upcoming = snapshot.recurring().stream()
                .filter(p -> !p.incoming() && !p.nextExpectedDate().isAfter(horizon))
                .map(p -> AnalysisService.recurring(p, categoryById))
                .sorted((a, b) -> a.nextExpectedDate().compareTo(b.nextExpectedDate()))
                .toList();

        List<DashboardResponse.SetupStep> setup = List.of(
                new DashboardResponse.SetupStep(
                        "profile",
                        "Tell us about you",
                        "Your pay, tax code and KiwiSaver settings.",
                        snapshot.profile().getOnboardedAt() != null,
                        "/settings/profile"),
                new DashboardResponse.SetupStep(
                        "bank",
                        "Connect your bank",
                        "Bring in transactions automatically with Akahu.",
                        hasBankFeed || !snapshot.history().isEmpty(),
                        "/connect"),
                new DashboardResponse.SetupStep(
                        "income",
                        "Add your income",
                        "So we can plan with your real take-home pay.",
                        snapshot.incomeBasis() != IncomeBasis.NONE,
                        "/settings/income"),
                new DashboardResponse.SetupStep(
                        "budget",
                        "Create a budget",
                        "Built from what you actually spend.",
                        budget.isPresent(),
                        "/budget"),
                new DashboardResponse.SetupStep(
                        "emergency-fund",
                        "Choose your emergency fund account",
                        "Tell us where your safety net lives.",
                        snapshot.accounts().stream().anyMatch(Account::isIncludedInEmergencyFund),
                        "/emergency-fund"),
                new DashboardResponse.SetupStep(
                        "goal", "Set a goal", "Something to save towards.", !goalViews.isEmpty(), "/goals"));

        return new DashboardResponse(
                users.findById(userId).map(User::getDisplayName).orElse(""),
                snapshot.today(),
                AnalysisResponses.Month.from(snapshot.thisMonth(), false),
                analysis.typical(snapshot),
                MoneyResponse.of(snapshot.netWorth()),
                MoneyResponse.of(Money.max(Money.ZERO, snapshot.liquidBalance().minus(fund.current()))),
                new DashboardResponse.Score(
                        progressView.score().score(),
                        progressView.score().band(),
                        progressView.score().bandLabel(),
                        progressView.score().nextStep()),
                progressView.streaks(),
                new DashboardResponse.EmergencyFund(
                        MoneyResponse.of(fund.target()),
                        MoneyResponse.of(fund.current()),
                        fund.progress(),
                        fund.monthsCovered(),
                        fund.targetMonths(),
                        fund.status()),
                budget.map(b -> new DashboardResponse.Budget(
                                b.progress().totalLimit(),
                                b.progress().totalSpent(),
                                b.progress().totalRemaining(),
                                b.progress().linesOver(),
                                b.progress().linesAtRisk(),
                                b.progress().dayOfMonth(),
                                b.progress().daysInMonth()))
                        .orElse(null),
                goalViews.stream()
                        .filter(g -> g.status() == GoalStatus.ACTIVE)
                        .limit(MAX_GOALS)
                        .toList(),
                upcoming,
                insights(snapshot, fund, categoryById, budget, goalViews).stream()
                        .limit(MAX_INSIGHTS)
                        .toList(),
                setup,
                progressView.unlocked(),
                progressView.total(),
                snapshot.accounts().stream().noneMatch(Account::isIncludedInEmergencyFund)
                        && preferences.remindAboutEmergencyFund(userId),
                thisMonthByCategory(snapshot, categoryById, budget));
    }

    @Transactional(readOnly = true)
    List<InsightResponse> insights(UUID userId) {
        FinancialSnapshot snapshot = snapshots.snapshot(userId);
        return insights(
                snapshot,
                emergencyFund.plan(snapshot),
                categories.byId(userId),
                budgets.current(userId, null),
                goals.list(userId));
    }

    private List<InsightResponse> insights(
            FinancialSnapshot snapshot,
            EmergencyFundPlan fund,
            Map<UUID, Category> categoryById,
            Optional<BudgetResponses.Current> budget,
            List<GoalResponses.View> goalViews) {
        InsightContext context = context(snapshot, fund, categoryById, budget, goalViews);
        return insightGenerator
                .generate(new InsightRequest(
                        snapshot.cashflow(), snapshot.spending(), snapshot.recurring(), fund, context))
                .stream()
                .map(insight -> new InsightResponse(
                        insight.key(),
                        insight.tone(),
                        insight.title(),
                        insight.message(),
                        MoneyResponse.of(insight.amount()),
                        AnalysisService.category(insight.category(), categoryById),
                        insight.action()))
                .toList();
    }

    private List<DashboardResponse.CategoryMonth> thisMonthByCategory(
            FinancialSnapshot snapshot, Map<UUID, Category> categoryById, Optional<BudgetResponses.Current> budget) {
        Map<String, Money> typical = snapshot.spending().stream()
                .filter(spending -> !spending.isUncategorised())
                .collect(Collectors.toMap(spending -> spending.category().id(), CategorySpending::monthlyMedian));
        Map<String, MoneyResponse> limits = budget.map(current -> current.progress().lines().stream()
                        .filter(line -> line.category() != null)
                        .collect(
                                Collectors.toMap(line -> line.category().id().toString(), BudgetResponses.Line::limit)))
                .orElse(Map.of());
        YearMonth month = snapshot.currentMonth();
        return spendingAnalyzer.byCategory(snapshot.history(), new MonthRange(month, month)).stream()
                .filter(spending -> spending.total().isPositive())
                .sorted(Comparator.comparing(CategorySpending::total).reversed())
                .map(spending -> {
                    String id = spending.isUncategorised()
                            ? null
                            : spending.category().id();
                    return new DashboardResponse.CategoryMonth(
                            AnalysisService.category(spending.category(), categoryById),
                            MoneyResponse.of(spending.total()),
                            MoneyResponse.of(id == null ? Money.ZERO : typical.getOrDefault(id, Money.ZERO)),
                            id == null ? null : limits.get(id));
                })
                .toList();
    }

    /**
     * The person's situation today, which insights read alongside their spending history.
     */
    private InsightContext context(
            FinancialSnapshot snapshot,
            EmergencyFundPlan fund,
            Map<UUID, Category> categoryById,
            Optional<BudgetResponses.Current> budget,
            List<GoalResponses.View> goalViews) {
        Optional<Account> fundAccount = snapshot.accounts().stream()
                .filter(Account::isIncludedInEmergencyFund)
                .findFirst();
        Money withdrawn = fundAccount
                .map(account -> transactions.netChange(
                        snapshot.userId(), account.getId(), snapshot.today().minusDays(30), snapshot.today()))
                .filter(Money::isNegative)
                .map(Money::negate)
                .orElse(Money.ZERO);
        Map<UUID, String> goalNames =
                goalViews.stream().collect(Collectors.toMap(GoalResponses.View::id, GoalResponses.View::name));
        List<InsightContext.GoalNote> goalNotes = goalPlans.plan(snapshot, fund, goalViews).advice().stream()
                .filter(advice -> goalNames.containsKey(advice.goalId()))
                .map(advice -> new InsightContext.GoalNote(
                        goalNames.get(advice.goalId()),
                        advice.kind(),
                        advice.urgency(),
                        advice.title(),
                        advice.detail()))
                .toList();
        return new InsightContext(
                snapshot.today(),
                snapshot.thisMonth(),
                snapshot.liquidBalance().minus(fund.current()),
                snapshot.consumerDebt(),
                fundAccount.isPresent(),
                withdrawn,
                budget.map(current -> new InsightContext.Budget(
                                current.progress().dayOfMonth(),
                                current.progress().daysInMonth(),
                                Money.ofCents(current.progress().totalLimit().cents()),
                                Money.ofCents(current.progress().totalSpent().cents()),
                                current.progress().lines().stream()
                                        .map(line -> new InsightContext.Line(
                                                line.category() == null
                                                        ? null
                                                        : Optional.ofNullable(categoryById.get(line.category()
                                                                        .id()))
                                                                .map(Category::toRef)
                                                                .orElse(null),
                                                Money.ofCents(line.limit().cents()),
                                                Money.ofCents(line.spent().cents())))
                                        .toList()))
                        .orElse(null),
                goalNotes);
    }
}
