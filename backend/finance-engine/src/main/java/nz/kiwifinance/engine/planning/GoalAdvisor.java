package nz.kiwifinance.engine.planning;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import nz.kiwifinance.engine.money.Money;

/**
 * Shares what a person can save each month across their goals, most urgent first, and suggests
 * one change per goal: save more, save less, move the date or adjust the target. An emergency fund
 * that is not yet funded is served first, because it protects every other goal.
 */
public final class GoalAdvisor {

    /** Goals due within this many months are treated as fixed dates; the target bends instead. */
    static final int URGENT_MONTHS = 6;

    private static final Money WHOLE_DOLLAR = Money.ofDollars(1);
    private static final Money TEN_DOLLARS = Money.ofDollars(10);
    private static final DateTimeFormatter MONTH_YEAR = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.of("en", "NZ"));

    public GoalPlan plan(GoalPlanRequest request) {
        Money available = Money.max(Money.ZERO, request.monthlySurplus());
        Money reserved = Money.min(available, Money.max(Money.ZERO, request.emergencyFundMonthly()));
        Money pool = available.minus(reserved);

        List<GoalPlanRequest.Goal> open = request.goals().stream()
                .filter(goal -> goal.remaining().isPositive())
                .sorted(urgency(request.today()))
                .toList();

        Map<String, Money> allocation = new LinkedHashMap<>();
        Money left = pool;
        for (GoalPlanRequest.Goal goal : open) {
            if (goal.targetDate() != null) {
                Money required = required(goal, request.today());
                Money given = Money.min(required, left);
                allocation.put(goal.id(), given);
                left = left.minus(given);
            }
        }
        List<GoalPlanRequest.Goal> flexible =
                open.stream().filter(goal -> goal.targetDate() == null).toList();
        for (int index = 0; index < flexible.size(); index++) {
            GoalPlanRequest.Goal goal = flexible.get(index);
            Money fairShare = left.dividedBy(flexible.size() - index);
            Money given =
                    goal.monthlyContribution().isPositive() ? Money.min(goal.monthlyContribution(), left) : fairShare;
            allocation.put(goal.id(), given);
            left = left.minus(given);
        }

        List<GoalPlan.Advice> advice = new ArrayList<>();
        for (GoalPlanRequest.Goal goal : open) {
            advice.add(advise(goal, allocation.getOrDefault(goal.id(), Money.ZERO), request.today()));
        }
        return new GoalPlan(available, reserved, pool, left, advice, summary(request, available, reserved, left));
    }

    private static GoalPlan.Advice advise(GoalPlanRequest.Goal goal, Money allocated, LocalDate today) {
        Money current = goal.monthlyContribution();
        if (goal.targetDate() == null) {
            if (!current.isPositive()) {
                if (!allocated.isPositive()) {
                    return advice(
                            goal,
                            GoalPlan.Urgency.FLEXIBLE,
                            allocated,
                            GoalPlan.Kind.REVIEW_BUDGET,
                            "Free up some money for this goal",
                            "There's nothing left over each month once your other goals are paid. Trimming your"
                                    + " budget is the quickest way to get this one moving.",
                            null,
                            null,
                            null);
                }
                Money suggested = allocated.roundUpTo(TEN_DOLLARS);
                return advice(
                        goal,
                        GoalPlan.Urgency.FLEXIBLE,
                        allocated,
                        GoalPlan.Kind.SET_CONTRIBUTION,
                        "Save " + suggested.formatWhole() + " a month",
                        "That would get you there by " + monthYear(finish(today, goal.remaining(), suggested))
                                + ", using money you usually have left over.",
                        suggested,
                        null,
                        null);
            }
            if (allocated.isLessThan(current)) {
                return advice(
                        goal,
                        GoalPlan.Urgency.FLEXIBLE,
                        allocated,
                        GoalPlan.Kind.REDUCE_CONTRIBUTION,
                        "Ease off to " + allocated.formatWhole() + " a month",
                        "You've promised more to your goals than you usually have spare. Saving "
                                + allocated.formatWhole() + " keeps this goal moving without overstretching.",
                        allocated,
                        null,
                        null);
            }
            return advice(
                    goal,
                    GoalPlan.Urgency.FLEXIBLE,
                    allocated,
                    GoalPlan.Kind.ON_TRACK,
                    "On track",
                    "At " + current.formatWhole() + " a month you'll get there by "
                            + monthYear(finish(today, goal.remaining(), current)) + ".",
                    null,
                    null,
                    null);
        }

        long months = monthsLeft(goal, today);
        GoalPlan.Urgency urgency =
                months <= 3 ? GoalPlan.Urgency.URGENT : months <= 12 ? GoalPlan.Urgency.SOON : GoalPlan.Urgency.LATER;
        Money required = required(goal, today);
        String due = monthYear(goal.targetDate());

        if (!allocated.isLessThan(required)) {
            if (current.isLessThan(required)) {
                return advice(
                        goal,
                        urgency,
                        allocated,
                        GoalPlan.Kind.INCREASE_CONTRIBUTION,
                        "Save " + required.formatWhole() + " a month",
                        "You're putting aside " + current.formatWhole() + " a month. Raising it to "
                                + required.formatWhole() + " finishes this goal by " + due + ", and you can afford it.",
                        required,
                        null,
                        null);
            }
            if (current.isGreaterThan(required.times(5).dividedBy(4).plus(TEN_DOLLARS))) {
                return advice(
                        goal,
                        urgency,
                        allocated,
                        GoalPlan.Kind.REDUCE_CONTRIBUTION,
                        "You could save " + required.formatWhole() + " a month",
                        "That still finishes by " + due + " and frees "
                                + current.minus(required).formatWhole() + " a month for your other goals.",
                        required,
                        null,
                        null);
            }
            return advice(
                    goal,
                    urgency,
                    allocated,
                    GoalPlan.Kind.ON_TRACK,
                    "On track for " + due,
                    "Keep saving " + current.formatWhole() + " a month and you'll make it.",
                    null,
                    null,
                    null);
        }

        if (months <= URGENT_MONTHS) {
            Money reachable = goal.saved().plus(allocated.times(months));
            Money suggestedTarget = Money.ofCents(reachable.cents() / TEN_DOLLARS.cents() * TEN_DOLLARS.cents());
            return advice(
                    goal,
                    urgency,
                    allocated,
                    GoalPlan.Kind.LOWER_TARGET,
                    "Aim for " + suggestedTarget.formatWhole() + " by " + due,
                    "This goal is due soon and " + required.formatWhole() + " a month is more than you usually"
                            + " have spare. A target of " + suggestedTarget.formatWhole()
                            + " is within reach by then.",
                    allocated.isPositive() ? allocated : null,
                    null,
                    suggestedTarget);
        }
        if (!allocated.isPositive()) {
            return advice(
                    goal,
                    urgency,
                    allocated,
                    GoalPlan.Kind.REVIEW_BUDGET,
                    "Free up some money for this goal",
                    "Your more urgent goals use everything you have spare right now. Trimming your budget, or"
                            + " moving this date, would get it going.",
                    null,
                    null,
                    null);
        }
        Money suggested = allocated.roundUpTo(WHOLE_DOLLAR);
        LocalDate realistic = finish(today, goal.remaining(), suggested);
        return advice(
                goal,
                urgency,
                allocated,
                GoalPlan.Kind.EXTEND_DATE,
                "Move the date to " + monthYear(realistic),
                "Finishing by " + due + " needs " + required.formatWhole() + " a month. At "
                        + suggested.formatWhole() + " a month, which fits your budget, you'll get there by "
                        + monthYear(realistic) + ".",
                suggested,
                realistic,
                null);
    }

    private static GoalPlan.Advice advice(
            GoalPlanRequest.Goal goal,
            GoalPlan.Urgency urgency,
            Money allocated,
            GoalPlan.Kind kind,
            String title,
            String detail,
            Money suggestedMonthly,
            LocalDate suggestedDate,
            Money suggestedTarget) {
        return new GoalPlan.Advice(
                goal.id(), urgency, allocated, kind, title, detail, suggestedMonthly, suggestedDate, suggestedTarget);
    }

    private static String summary(GoalPlanRequest request, Money available, Money reserved, Money left) {
        if (!available.isPositive()) {
            return "You're spending about as much as you earn, so there's nothing spare for goals right now."
                    + " A budget is the best place to start.";
        }
        StringBuilder text = new StringBuilder("You usually have about ")
                .append(available.formatWhole())
                .append(" a month to save.");
        if (reserved.isPositive()) {
            text.append(" We've kept ")
                    .append(reserved.formatWhole())
                    .append(" of it for your emergency fund, which comes first.");
        }
        text.append(" The rest is shared across your goals, soonest first.");
        if (left.isGreaterThan(TEN_DOLLARS) && !request.goals().isEmpty()) {
            text.append(" That leaves ").append(left.formatWhole()).append(" a month unplanned.");
        }
        return text.toString();
    }

    private static Comparator<GoalPlanRequest.Goal> urgency(LocalDate today) {
        return Comparator.<GoalPlanRequest.Goal, Boolean>comparing(goal -> goal.targetDate() == null)
                .thenComparing(goal -> goal.targetDate() == null ? 0 : monthsLeft(goal, today))
                .thenComparingInt(GoalPlanRequest.Goal::priority);
    }

    static long monthsLeft(GoalPlanRequest.Goal goal, LocalDate today) {
        return Math.max(
                1,
                ChronoUnit.MONTHS.between(
                        today.withDayOfMonth(1), goal.targetDate().withDayOfMonth(1)));
    }

    static Money required(GoalPlanRequest.Goal goal, LocalDate today) {
        long months = monthsLeft(goal, today);
        return Money.ofCents((goal.remaining().cents() + months - 1) / months).roundUpTo(WHOLE_DOLLAR);
    }

    private static LocalDate finish(LocalDate today, Money remaining, Money monthly) {
        long months = (remaining.cents() + monthly.cents() - 1) / monthly.cents();
        return today.plusMonths(months);
    }

    private static String monthYear(LocalDate date) {
        return date.format(MONTH_YEAR);
    }
}
