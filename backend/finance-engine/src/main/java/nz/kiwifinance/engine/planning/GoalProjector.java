package nz.kiwifinance.engine.planning;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Locale;
import java.util.OptionalInt;
import java.util.stream.IntStream;
import nz.kiwifinance.engine.explain.Explanation;
import nz.kiwifinance.engine.money.Money;

public final class GoalProjector {

    private static final DateTimeFormatter MONTH_YEAR = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.of("en", "NZ"));

    /**
     * @param monthlyContribution what the person plans to, or recently did, put in each month
     * @param targetDate the date the person wants to reach the goal by, or {@code null}
     */
    public GoalProjection project(
            Money target,
            Money saved,
            Money monthlyContribution,
            LocalDate targetDate,
            LocalDate today,
            BigDecimal interestRate) {
        var projector = new SavingsProjector(interestRate);
        Money remaining = Money.max(Money.ZERO, target.minus(saved));
        BigDecimal progress = target.isPositive()
                ? saved.ratioOf(target).max(BigDecimal.ZERO).min(BigDecimal.ONE).setScale(4, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

        OptionalInt months = projector.monthsToReach(target, saved, monthlyContribution);
        Integer monthsToGoal = months.isPresent() ? months.getAsInt() : null;
        LocalDate projectedDate = monthsToGoal == null ? null : today.plusMonths(monthsToGoal);

        Money requiredMonthly = null;
        if (targetDate != null) {
            int monthsLeft = (int) Math.max(0, ChronoUnit.MONTHS.between(today, targetDate));
            requiredMonthly = projector.requiredMonthlyContribution(target, saved, monthsLeft);
        }

        GoalProjection.Status status;
        if (remaining.isZero()) {
            status = GoalProjection.Status.ACHIEVED;
        } else if (!saved.isPositive() && !monthlyContribution.isPositive()) {
            status = GoalProjection.Status.NOT_STARTED;
        } else if (targetDate == null) {
            status = projectedDate != null ? GoalProjection.Status.ON_TRACK : GoalProjection.Status.BEHIND;
        } else {
            status = projectedDate != null && !projectedDate.isAfter(targetDate)
                    ? GoalProjection.Status.ON_TRACK
                    : GoalProjection.Status.BEHIND;
        }

        List<GoalProjection.Milestone> milestones = IntStream.of(25, 50, 75, 100)
                .mapToObj(percent -> {
                    Money amount = target.times(BigDecimal.valueOf(percent).divide(BigDecimal.valueOf(100)));
                    return new GoalProjection.Milestone(percent, amount, saved.isAtLeast(amount));
                })
                .toList();

        return new GoalProjection(
                progress,
                remaining,
                monthsToGoal,
                projectedDate,
                requiredMonthly,
                status,
                milestones,
                explain(
                        target,
                        saved,
                        remaining,
                        monthlyContribution,
                        projectedDate,
                        targetDate,
                        requiredMonthly,
                        status));
    }

    private static Explanation explain(
            Money target,
            Money saved,
            Money remaining,
            Money monthly,
            LocalDate projectedDate,
            LocalDate targetDate,
            Money requiredMonthly,
            GoalProjection.Status status) {
        String summary = switch (status) {
            case ACHIEVED -> "Goal reached. You have saved %s.".formatted(saved.formatWhole());
            case NOT_STARTED -> "Add a first contribution to start tracking this goal.";
            case ON_TRACK ->
                targetDate != null
                        ? "On track to finish by %s, ahead of your %s target."
                                .formatted(MONTH_YEAR.format(projectedDate), MONTH_YEAR.format(targetDate))
                        : "At %s a month you will reach this goal by %s."
                                .formatted(monthly.formatWhole(), MONTH_YEAR.format(projectedDate));
            case BEHIND ->
                requiredMonthly != null
                        ? "To finish by %s, save %s a month."
                                .formatted(MONTH_YEAR.format(targetDate), requiredMonthly.formatWhole())
                        : "Increase your monthly contribution to reach this goal.";
        };
        var builder = Explanation.builder()
                .summary(summary)
                .step("Target", target.format())
                .step("Saved so far", saved.format())
                .step("Still to save", remaining.format())
                .step("Recent monthly contribution", monthly.format());
        if (requiredMonthly != null) {
            builder.step("Needed each month to finish on time", requiredMonthly.format());
        }
        return builder.build();
    }
}
