package nz.kiwifinance.goal;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import nz.kiwifinance.analysis.FinancialSnapshot;
import nz.kiwifinance.analysis.FinancialSnapshotService;
import nz.kiwifinance.common.web.MoneyResponse;
import nz.kiwifinance.emergencyfund.EmergencyFundService;
import nz.kiwifinance.engine.emergencyfund.EmergencyFundPlan;
import nz.kiwifinance.engine.money.Money;
import nz.kiwifinance.engine.planning.GoalAdvisor;
import nz.kiwifinance.engine.planning.GoalPlan;
import nz.kiwifinance.engine.planning.GoalPlanRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Suggests how to share what a person can save across their goals, from their real income,
 * spending and emergency fund.
 */
@Service
@RequiredArgsConstructor
public class GoalPlanService {

    private final GoalService goals;
    private final FinancialSnapshotService snapshots;
    private final EmergencyFundService emergencyFund;
    private final GoalAdvisor advisor = new GoalAdvisor();

    @Transactional(readOnly = true)
    public GoalResponses.Plan plan(UUID userId) {
        FinancialSnapshot snapshot = snapshots.snapshot(userId);
        return plan(snapshot, emergencyFund.plan(snapshot), goals.list(userId));
    }

    /**
     * Plans from figures the caller already has, so screens that show several things at once work
     * them out only once.
     */
    public GoalResponses.Plan plan(FinancialSnapshot snapshot, EmergencyFundPlan fund, List<GoalResponses.View> all) {
        List<GoalResponses.View> active =
                all.stream().filter(goal -> goal.status() == GoalStatus.ACTIVE).toList();
        GoalPlan plan = advisor.plan(new GoalPlanRequest(
                snapshot.today(),
                snapshot.monthlySurplus(),
                fund.status() == EmergencyFundPlan.Status.FUNDED ? Money.ZERO : fund.suggestedMonthlyContribution(),
                active.stream()
                        .map(goal -> new GoalPlanRequest.Goal(
                                goal.id().toString(),
                                Money.ofCents(goal.target().cents()),
                                Money.ofCents(goal.saved().cents()),
                                goal.targetDate(),
                                goal.priority(),
                                Money.ofCents(goal.monthlyContribution().cents())))
                        .toList()));
        Map<String, GoalResponses.View> byId =
                active.stream().collect(Collectors.toMap(goal -> goal.id().toString(), Function.identity()));
        return new GoalResponses.Plan(
                MoneyResponse.of(plan.available()),
                MoneyResponse.of(plan.emergencyFund()),
                MoneyResponse.of(plan.forGoals()),
                MoneyResponse.of(plan.unplanned()),
                plan.summary(),
                plan.advice().stream()
                        .filter(advice -> byId.containsKey(advice.goalId()))
                        .map(advice -> new GoalResponses.Advice(
                                UUID.fromString(advice.goalId()),
                                advice.urgency(),
                                MoneyResponse.of(advice.allocated()),
                                advice.kind(),
                                advice.title(),
                                advice.detail(),
                                advice.suggestedMonthly() == null ? null : MoneyResponse.of(advice.suggestedMonthly()),
                                advice.suggestedDate(),
                                advice.suggestedTarget() == null ? null : MoneyResponse.of(advice.suggestedTarget())))
                        .toList());
    }
}
