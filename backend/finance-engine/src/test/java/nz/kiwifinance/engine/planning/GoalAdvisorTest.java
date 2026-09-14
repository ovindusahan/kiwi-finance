package nz.kiwifinance.engine.planning;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.List;
import nz.kiwifinance.engine.money.Money;
import org.junit.jupiter.api.Test;

class GoalAdvisorTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 2);

    private final GoalAdvisor advisor = new GoalAdvisor();

    @Test
    void servesTheEmergencyFundAndUrgentGoalsFirst() {
        GoalPlan plan = advisor.plan(new GoalPlanRequest(
                TODAY,
                Money.ofDollars(1_000),
                Money.ofDollars(200),
                List.of(
                        goal("holiday", 6_000, 0, TODAY.plusMonths(30), 0),
                        goal("laptop", 2_400, 0, TODAY.plusMonths(4), 0))));

        assertThat(plan.emergencyFund()).isEqualTo(Money.ofDollars(200));
        assertThat(plan.forGoals()).isEqualTo(Money.ofDollars(800));
        assertThat(plan.advice()).extracting(GoalPlan.Advice::goalId).containsExactly("laptop", "holiday");
        GoalPlan.Advice laptop = plan.advice().getFirst();
        assertThat(laptop.urgency()).isEqualTo(GoalPlan.Urgency.SOON);
        assertThat(laptop.allocated()).isEqualTo(Money.ofDollars(600));
        assertThat(laptop.kind()).isEqualTo(GoalPlan.Kind.INCREASE_CONTRIBUTION);
        assertThat(laptop.suggestedMonthly()).isEqualTo(Money.ofDollars(600));

        GoalPlan.Advice holiday = plan.advice().get(1);
        assertThat(holiday.allocated()).isEqualTo(Money.ofDollars(200));
        assertThat(holiday.kind()).isEqualTo(GoalPlan.Kind.INCREASE_CONTRIBUTION);
        assertThat(plan.summary()).contains("$1,000", "emergency fund");
    }

    @Test
    void movesTheDateOfADistantGoalThatDoesNotFit() {
        GoalPlan plan = advisor.plan(new GoalPlanRequest(
                TODAY,
                Money.ofDollars(300),
                Money.ZERO,
                List.of(goal("deposit", 20_000, 2_000, TODAY.plusMonths(24), 300))));

        GoalPlan.Advice deposit = plan.advice().getFirst();
        assertThat(deposit.kind()).isEqualTo(GoalPlan.Kind.EXTEND_DATE);
        assertThat(deposit.suggestedMonthly()).isEqualTo(Money.ofDollars(300));
        assertThat(deposit.suggestedDate()).isEqualTo(TODAY.plusMonths(60));
        assertThat(deposit.urgency()).isEqualTo(GoalPlan.Urgency.LATER);
    }

    @Test
    void lowersTheTargetOfAnUrgentGoalThatDoesNotFit() {
        GoalPlan plan = advisor.plan(new GoalPlanRequest(
                TODAY,
                Money.ofDollars(250),
                Money.ZERO,
                List.of(goal("wedding", 3_000, 500, TODAY.plusMonths(2), 250))));

        GoalPlan.Advice wedding = plan.advice().getFirst();
        assertThat(wedding.urgency()).isEqualTo(GoalPlan.Urgency.URGENT);
        assertThat(wedding.kind()).isEqualTo(GoalPlan.Kind.LOWER_TARGET);
        assertThat(wedding.suggestedTarget()).isEqualTo(Money.ofDollars(1_000));
    }

    @Test
    void freesMoneyFromAGoalThatIsAhead() {
        GoalPlan plan = advisor.plan(new GoalPlanRequest(
                TODAY, Money.ofDollars(2_000), Money.ZERO, List.of(goal("car", 1_200, 0, TODAY.plusMonths(12), 500))));

        GoalPlan.Advice car = plan.advice().getFirst();
        assertThat(car.kind()).isEqualTo(GoalPlan.Kind.REDUCE_CONTRIBUTION);
        assertThat(car.suggestedMonthly()).isEqualTo(Money.ofDollars(100));
    }

    @Test
    void suggestsAContributionForAnOpenEndedGoal() {
        GoalPlan plan = advisor.plan(new GoalPlanRequest(
                TODAY,
                Money.ofDollars(405),
                Money.ZERO,
                List.of(goal("rainy", 4_000, 0, null, 0), goal("bike", 1_000, 1_000, null, 0))));

        assertThat(plan.advice()).hasSize(1);
        GoalPlan.Advice rainy = plan.advice().getFirst();
        assertThat(rainy.kind()).isEqualTo(GoalPlan.Kind.SET_CONTRIBUTION);
        assertThat(rainy.suggestedMonthly()).isEqualTo(Money.ofDollars(410));
        assertThat(rainy.urgency()).isEqualTo(GoalPlan.Urgency.FLEXIBLE);
    }

    @Test
    void explainsWhenThereIsNothingSpare() {
        GoalPlan plan = advisor.plan(new GoalPlanRequest(
                TODAY, Money.ofDollars(-150), Money.ZERO, List.of(goal("rainy", 4_000, 0, TODAY.plusMonths(20), 0))));

        assertThat(plan.available()).isEqualTo(Money.ZERO);
        assertThat(plan.advice().getFirst().kind()).isEqualTo(GoalPlan.Kind.REVIEW_BUDGET);
        assertThat(plan.summary()).contains("nothing spare");
    }

    private static GoalPlanRequest.Goal goal(String id, long target, long saved, LocalDate date, long monthly) {
        return new GoalPlanRequest.Goal(
                id, Money.ofDollars(target), Money.ofDollars(saved), date, 0, Money.ofDollars(monthly));
    }
}
