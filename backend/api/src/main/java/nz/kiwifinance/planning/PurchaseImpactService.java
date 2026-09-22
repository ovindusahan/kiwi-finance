package nz.kiwifinance.planning;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import nz.kiwifinance.account.AccountType;
import nz.kiwifinance.analysis.FinancialSnapshot;
import nz.kiwifinance.analysis.FinancialSnapshotService;
import nz.kiwifinance.category.CategoryResponse;
import nz.kiwifinance.category.CategoryService;
import nz.kiwifinance.common.web.MoneyResponse;
import nz.kiwifinance.emergencyfund.EmergencyFundService;
import nz.kiwifinance.engine.analysis.CategorySpending;
import nz.kiwifinance.engine.emergencyfund.EmergencyFundPlan;
import nz.kiwifinance.engine.money.Money;
import nz.kiwifinance.engine.planning.GoalPlan;
import nz.kiwifinance.engine.planning.PurchaseImpact;
import nz.kiwifinance.engine.planning.PurchaseImpactCalculator;
import nz.kiwifinance.engine.planning.PurchaseImpactRequest;
import nz.kiwifinance.goal.GoalPlanService;
import nz.kiwifinance.goal.GoalResponses;
import nz.kiwifinance.goal.GoalService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Weighs up a big purchase against the person's real income, spending, savings and goals.
 */
@Service
@RequiredArgsConstructor
class PurchaseImpactService {

    private static final List<GoalPlan.Kind> SLIPPING =
            List.of(GoalPlan.Kind.EXTEND_DATE, GoalPlan.Kind.LOWER_TARGET, GoalPlan.Kind.REVIEW_BUDGET);

    private final FinancialSnapshotService snapshots;
    private final EmergencyFundService emergencyFund;
    private final GoalService goals;
    private final GoalPlanService goalPlans;
    private final CategoryService categories;
    private final PurchaseImpactCalculator calculator = new PurchaseImpactCalculator();

    @Transactional(readOnly = true)
    PlanningResponses.Impact assess(UUID userId, PlanningRequests.Impact request) {
        FinancialSnapshot snapshot = snapshots.snapshot(userId);
        EmergencyFundPlan fund = emergencyFund.plan(snapshot);
        Money kiwiSaver = Money.sum(snapshot.accounts().stream()
                .filter(account -> account.getType() == AccountType.KIWISAVER)
                .map(account -> Money.ofCents(account.getCurrentBalanceCents()))
                .toList());
        String rentId = categories
                .system("rent")
                .map(category -> category.getId().toString())
                .orElse("");
        Money rent = snapshot.spending().stream()
                .filter(spending -> !spending.isUncategorised())
                .filter(spending -> rentId.equals(spending.category().id()))
                .map(CategorySpending::monthlyMedian)
                .findFirst()
                .orElse(Money.ZERO);

        PurchaseImpact impact = calculator.assess(new PurchaseImpactRequest(
                request.kind(),
                Money.ofCents(request.priceCents()),
                cents(request.depositCents()),
                request.annualRate(),
                request.termMonths(),
                cents(request.upfrontCostsCents()),
                cents(request.ownershipCostsCents()),
                request.firstHome(),
                snapshot.today(),
                snapshot.monthlyIncome(),
                snapshot.monthlySurplus(),
                Money.max(Money.ZERO, snapshot.liquidBalance().minus(fund.current())),
                kiwiSaver,
                rent));

        Map<String, CategoryResponse> bySlug = categories.byId(userId).values().stream()
                .filter(category -> category.getSlug() != null)
                .collect(Collectors.toMap(category -> category.getSlug(), CategoryResponse::from, (a, b) -> a));

        return new PlanningResponses.Impact(
                impact.kind(),
                MoneyResponse.of(impact.price()),
                MoneyResponse.of(impact.deposit()),
                MoneyResponse.of(impact.recommendedDeposit()),
                impact.depositGuidance(),
                impact.loan() == null ? null : PlanningResponses.Loan.from(impact.loan()),
                costs(impact.upfrontCosts(), bySlug),
                MoneyResponse.of(impact.upfrontTotal()),
                costs(impact.monthlyCosts(), bySlug),
                MoneyResponse.of(impact.monthlyTotal()),
                MoneyResponse.of(snapshot.monthlyIncome()),
                MoneyResponse.of(impact.surplusBefore()),
                MoneyResponse.of(impact.surplusAfter()),
                impact.savingsRateBefore(),
                impact.savingsRateAfter(),
                impact.costToIncome(),
                MoneyResponse.of(impact.cashNeeded()),
                MoneyResponse.of(impact.cashAvailable()),
                MoneyResponse.of(impact.kiwiSaverAvailable()),
                MoneyResponse.of(impact.cashShortfall()),
                impact.monthsToSave(),
                impact.readyBy(),
                impact.options().stream()
                        .map(option -> new PlanningResponses.Option(
                                option.termMonths(),
                                MoneyResponse.of(option.monthlyRepayment()),
                                MoneyResponse.of(option.totalInterest()),
                                MoneyResponse.of(option.surplusAfter())))
                        .toList(),
                impact.verdict(),
                impact.headline(),
                impact.notes(),
                affectedGoals(snapshot, fund, userId, impact.monthlyTotal()));
    }

    /**
     * Goals that would fall behind if the purchase's monthly costs came out of what the person
     * usually has spare.
     */
    private List<PlanningResponses.AffectedGoal> affectedGoals(
            FinancialSnapshot snapshot, EmergencyFundPlan fund, UUID userId, Money monthlyCost) {
        List<GoalResponses.View> views = goals.list(userId);
        Map<UUID, GoalResponses.Advice> before = goalPlans.plan(snapshot, fund, views).advice().stream()
                .collect(Collectors.toMap(GoalResponses.Advice::goalId, advice -> advice));
        Map<UUID, String> names =
                views.stream().collect(Collectors.toMap(GoalResponses.View::id, GoalResponses.View::name));
        return goalPlans
                .plan(snapshot.withMonthlySurplus(snapshot.monthlySurplus().minus(monthlyCost)), fund, views)
                .advice()
                .stream()
                .filter(after -> SLIPPING.contains(after.kind()))
                .filter(after -> Optional.ofNullable(before.get(after.goalId()))
                        .map(advice -> !SLIPPING.contains(advice.kind())
                                || !advice.title().equals(after.title()))
                        .orElse(true))
                .map(after ->
                        new PlanningResponses.AffectedGoal(after.goalId(), names.get(after.goalId()), after.title()))
                .toList();
    }

    private static List<PlanningResponses.Cost> costs(
            List<PurchaseImpact.CostLine> lines, Map<String, CategoryResponse> bySlug) {
        return lines.stream()
                .map(line -> new PlanningResponses.Cost(
                        line.label(),
                        MoneyResponse.of(line.amount()),
                        line.budgetCategory() == null ? null : bySlug.get(line.budgetCategory())))
                .toList();
    }

    private static Money cents(Long cents) {
        return cents == null ? null : Money.ofCents(cents);
    }
}
