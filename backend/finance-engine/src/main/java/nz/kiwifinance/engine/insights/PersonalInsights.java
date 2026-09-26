package nz.kiwifinance.engine.insights;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Comparator;
import java.util.List;
import nz.kiwifinance.engine.analysis.CashflowAnalysis;
import nz.kiwifinance.engine.analysis.RecurringPayment;
import nz.kiwifinance.engine.emergencyfund.EmergencyFundPlan;
import nz.kiwifinance.engine.money.Money;
import nz.kiwifinance.engine.planning.GoalPlan;

/**
 * Insights about the person's situation today: their budget, goals, debt, upcoming bills and where
 * their money sits. These change as soon as the situation does, such as after a transfer.
 */
final class PersonalInsights {

    /** How far ahead to look for bills that need covering. */
    static final int BILL_WINDOW_DAYS = 14;

    private static final Money MIN_GAP = Money.ofDollars(50);
    private static final Money MIN_DEBT = Money.ofDollars(500);
    private static final Money MIN_PACE_GAP = Money.ofDollars(100);
    private static final int MAX_BUDGET_INSIGHTS = 2;
    private static final int MAX_GOAL_INSIGHTS = 2;

    private PersonalInsights() {}

    static void add(InsightRequest request, List<Insight> insights) {
        InsightContext context = request.context();
        if (context == null) {
            return;
        }
        bills(context, request.recurring(), insights);
        budget(context, request.cashflow(), insights);
        goals(context, insights);
        pace(context, request.cashflow(), insights);
        emergencyFundWithdrawal(context, request.emergencyFund(), insights);
        spareCash(context, request.cashflow(), request.emergencyFund(), insights);
        debt(context, request.cashflow(), insights);
    }

    private static void bills(InsightContext context, List<RecurringPayment> recurring, List<Insight> insights) {
        Money due = Money.sum(recurring.stream()
                .filter(payment -> !payment.incoming())
                .filter(payment -> !payment.nextExpectedDate().isBefore(context.today()))
                .filter(payment ->
                        !payment.nextExpectedDate().isAfter(context.today().plusDays(BILL_WINDOW_DAYS)))
                .map(RecurringPayment::typicalAmount)
                .toList());
        if (due.isPositive() && due.isGreaterThan(context.spendable())) {
            Money gap = due.minus(Money.max(Money.ZERO, context.spendable()));
            insights.add(new Insight(
                    "bills_exceed_balance",
                    Insight.Tone.WARNING,
                    "Bills due soon are more than your spending money",
                    "About %s of regular payments are due in the next two weeks, and you have %s outside your emergency fund. Moving %s across would cover them."
                            .formatted(
                                    due.formatWhole(),
                                    Money.max(Money.ZERO, context.spendable()).formatWhole(),
                                    gap.formatWhole()),
                    gap,
                    null,
                    Insight.Action.MOVE_MONEY));
        }
    }

    private static void budget(InsightContext context, CashflowAnalysis cashflow, List<Insight> insights) {
        InsightContext.Budget budget = context.budget();
        if (budget == null) {
            if (cashflow.monthsOfData() >= 1) {
                insights.add(new Insight(
                        "budget_missing",
                        Insight.Tone.NEUTRAL,
                        "Set a budget from your real spending",
                        "We've already worked out limits from what you usually spend. Reviewing them takes about a minute.",
                        null,
                        null,
                        Insight.Action.OPEN_BUDGET));
            }
            return;
        }
        int added = 0;
        List<InsightContext.Line> over = budget.lines().stream()
                .filter(line -> line.spent().isGreaterThan(line.limit()))
                .sorted(Comparator.comparing(
                                (InsightContext.Line line) -> line.spent().minus(line.limit()))
                        .reversed())
                .toList();
        for (InsightContext.Line line : over) {
            if (added == MAX_BUDGET_INSIGHTS) {
                break;
            }
            Money by = line.spent().minus(line.limit());
            insights.add(new Insight(
                    "budget_over",
                    Insight.Tone.WARNING,
                    "You're over your %s budget".formatted(name(line)),
                    "You've spent %s of %s this month, %s over. Holding back elsewhere keeps the total on track."
                            .formatted(line.spent().formatWhole(), line.limit().formatWhole(), by.formatWhole()),
                    by,
                    line.category(),
                    Insight.Action.OPEN_BUDGET));
            added++;
        }
        double pace = budget.pace();
        int daysLeft = budget.daysInMonth() - budget.dayOfMonth();
        List<InsightContext.Line> ahead = budget.lines().stream()
                .filter(line -> line.limit().isPositive() && !line.spent().isGreaterThan(line.limit()))
                .filter(line -> ratio(line.spent(), line.limit()) > Math.min(1, pace + 0.25)
                        && ratio(line.spent(), line.limit()) >= 0.5)
                .sorted(Comparator.comparingDouble((InsightContext.Line line) -> ratio(line.spent(), line.limit()))
                        .reversed())
                .toList();
        for (InsightContext.Line line : ahead) {
            if (added == MAX_BUDGET_INSIGHTS || daysLeft <= 0) {
                break;
            }
            insights.add(new Insight(
                    "budget_running_ahead",
                    Insight.Tone.WARNING,
                    "%s is running ahead of budget".formatted(capitalise(name(line))),
                    "You've used %d%% of it with %d days to go. %s is left for the rest of the month."
                            .formatted(
                                    Math.round(ratio(line.spent(), line.limit()) * 100),
                                    daysLeft,
                                    line.limit().minus(line.spent()).formatWhole()),
                    line.limit().minus(line.spent()),
                    line.category(),
                    Insight.Action.OPEN_BUDGET));
            added++;
        }
        if (added == 0 && pace >= 0.6 && budget.totalLimit().isPositive()) {
            Money expected = budget.totalLimit().times(BigDecimal.valueOf(pace));
            if (budget.totalSpent().isLessThan(expected.minus(MIN_GAP))) {
                insights.add(new Insight(
                        "budget_under",
                        Insight.Tone.POSITIVE,
                        "You're on track to finish under budget",
                        "You've spent %s of %s with %d days to go. Whatever's left at the end of the month can go to your goals."
                                .formatted(
                                        budget.totalSpent().formatWhole(),
                                        budget.totalLimit().formatWhole(),
                                        daysLeft),
                        budget.totalLimit().minus(budget.totalSpent()),
                        null,
                        Insight.Action.OPEN_BUDGET));
            }
        }
    }

    private static void goals(InsightContext context, List<Insight> insights) {
        context.goals().stream()
                .filter(goal -> goal.kind() != GoalPlan.Kind.ON_TRACK)
                .limit(MAX_GOAL_INSIGHTS)
                .forEach(goal -> insights.add(new Insight(
                        "goal_" + goal.kind().name().toLowerCase(java.util.Locale.ROOT),
                        switch (goal.kind()) {
                            case EXTEND_DATE, LOWER_TARGET, REVIEW_BUDGET -> Insight.Tone.WARNING;
                            default -> Insight.Tone.NEUTRAL;
                        },
                        "%s: %s".formatted(goal.name(), lowerFirst(goal.title())),
                        goal.detail(),
                        null,
                        null,
                        Insight.Action.OPEN_GOALS)));
    }

    private static void pace(InsightContext context, CashflowAnalysis cashflow, List<Insight> insights) {
        if (!cashflow.hasEnoughData() || context.thisMonth() == null) {
            return;
        }
        int day = context.today().getDayOfMonth();
        int days = context.today().lengthOfMonth();
        if (day < 7) {
            return;
        }
        Money expected = cashflow.typicalSpending()
                .times(BigDecimal.valueOf(day))
                .dividedBy(BigDecimal.valueOf(days), RoundingMode.HALF_UP);
        Money spent = context.thisMonth().spending();
        if (spent.isGreaterThan(expected.times(new BigDecimal("1.25")))
                && spent.minus(expected).isGreaterThan(MIN_PACE_GAP)) {
            insights.add(new Insight(
                    "spending_pace_high",
                    Insight.Tone.WARNING,
                    "You're spending faster than usual this month",
                    "%s so far, against about %s by this point in a typical month."
                            .formatted(spent.formatWhole(), expected.formatWhole()),
                    spent.minus(expected),
                    null,
                    Insight.Action.REVIEW_SPENDING));
        }
    }

    private static void emergencyFundWithdrawal(
            InsightContext context, EmergencyFundPlan plan, List<Insight> insights) {
        if (!context.emergencyFundWithdrawn().isPositive() || plan == null) {
            return;
        }
        String rebuild = plan.suggestedMonthlyContribution().isPositive() && plan.monthsToTarget() != null
                ? " Saving %s a month gets it back to target in about %d months."
                        .formatted(plan.suggestedMonthlyContribution().formatWhole(), plan.monthsToTarget())
                : "";
        insights.add(new Insight(
                "emergency_fund_used",
                plan.status() == EmergencyFundPlan.Status.FUNDED ? Insight.Tone.NEUTRAL : Insight.Tone.WARNING,
                "Rebuild your emergency fund",
                "You've taken %s out of it in the last month. That's what it's there for, and now it covers %s months."
                                .formatted(
                                        context.emergencyFundWithdrawn().formatWhole(),
                                        plan.monthsCovered()
                                                .stripTrailingZeros()
                                                .toPlainString())
                        + rebuild,
                context.emergencyFundWithdrawn(),
                null,
                Insight.Action.MOVE_MONEY));
    }

    private static void spareCash(
            InsightContext context, CashflowAnalysis cashflow, EmergencyFundPlan plan, List<Insight> insights) {
        if (plan == null
                || !context.emergencyFundChosen()
                || plan.status() == EmergencyFundPlan.Status.FUNDED
                || !cashflow.hasEnoughData()) {
            return;
        }
        Money cushion = cashflow.typicalSpending().times(new BigDecimal("1.5"));
        Money spare = Money.min(context.spendable().minus(cushion), plan.shortfall());
        if (spare.isGreaterThan(Money.ofDollars(200))) {
            insights.add(new Insight(
                    "spare_cash",
                    Insight.Tone.NEUTRAL,
                    "Put %s of spare cash to work".formatted(spare.formatWhole()),
                    "Your everyday accounts hold more than you usually need for six weeks of spending. Moving %s to your emergency fund brings it closer to %s."
                            .formatted(spare.formatWhole(), plan.target().formatWhole()),
                    spare,
                    null,
                    Insight.Action.MOVE_MONEY));
        }
    }

    private static void debt(InsightContext context, CashflowAnalysis cashflow, List<Insight> insights) {
        Money debt = context.consumerDebt();
        if (debt.isLessThan(MIN_DEBT)) {
            return;
        }
        Money cushion = cashflow.typicalEssentialSpending();
        if (context.spendable().isGreaterThan(debt.plus(cushion))) {
            insights.add(new Insight(
                    "debt_clearable",
                    Insight.Tone.NEUTRAL,
                    "You could clear your %s of card and loan debt".formatted(debt.formatWhole()),
                    "You have enough spare cash to pay it off and still keep a month of essentials. Card interest usually costs far more than savings earn.",
                    debt,
                    null,
                    Insight.Action.MOVE_MONEY));
        } else {
            insights.add(new Insight(
                    "debt_owing",
                    Insight.Tone.NEUTRAL,
                    "Paying down %s of debt".formatted(debt.formatWhole()),
                    "Interest on cards and loans usually costs more than savings earn, so extra repayments are often the best use of spare money after your emergency fund.",
                    debt,
                    null,
                    Insight.Action.OPEN_GOALS));
        }
    }

    private static double ratio(Money part, Money whole) {
        return whole.isZero() ? 0 : (double) part.cents() / whole.cents();
    }

    private static String name(InsightContext.Line line) {
        return line.category() == null ? "everything else" : line.category().name();
    }

    private static String capitalise(String text) {
        return text.isEmpty() ? text : Character.toUpperCase(text.charAt(0)) + text.substring(1);
    }

    private static String lowerFirst(String text) {
        return text.isEmpty() ? text : Character.toLowerCase(text.charAt(0)) + text.substring(1);
    }
}
