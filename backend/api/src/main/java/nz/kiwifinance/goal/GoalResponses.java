package nz.kiwifinance.goal;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import nz.kiwifinance.common.web.MoneyResponse;
import nz.kiwifinance.engine.explain.Explanation;
import nz.kiwifinance.engine.planning.GoalPlan;
import nz.kiwifinance.engine.planning.GoalProjection;
import org.jspecify.annotations.Nullable;

public final class GoalResponses {

    private GoalResponses() {}

    /**
     * @param daysLeft days until the target date, or {@code null} without one
     */
    @Schema(name = "Goal")
    public record View(
            UUID id,
            String name,
            GoalType type,
            MoneyResponse target,
            @Nullable LocalDate targetDate,
            @Nullable Long daysLeft,
            int priority,
            GoalStatus status,
            MoneyResponse monthlyContribution,
            @Nullable UUID linkedAccountId,
            @Nullable String linkedAccountName,
            MoneyResponse saved,
            MoneyResponse remaining,
            BigDecimal progress,
            Projection projection,
            @Nullable Instant achievedAt,
            Instant createdAt) {}

    @Schema(name = "GoalProjection")
    public record Projection(
            GoalProjection.Status status,
            @Nullable Integer monthsToGoal,
            @Nullable LocalDate projectedDate,
            @Nullable MoneyResponse requiredMonthly,
            MoneyResponse monthlyContribution,
            List<Milestone> milestones,
            Explanation explanation) {}

    /**
     * @param available what the person usually has spare each month
     * @param emergencyFund the part kept back for an emergency fund that is not yet funded
     * @param forGoals the part shared across goals
     * @param unplanned what is left once every goal has its share
     */
    @Schema(name = "GoalPlan")
    public record Plan(
            MoneyResponse available,
            MoneyResponse emergencyFund,
            MoneyResponse forGoals,
            MoneyResponse unplanned,
            String summary,
            List<Advice> advice) {}

    /**
     * A suggestion for one goal. Apply the suggested values that are present to follow it.
     *
     * @param allocated the goal's share of what the person has spare each month
     */
    @Schema(name = "GoalAdvice")
    public record Advice(
            UUID goalId,
            GoalPlan.Urgency urgency,
            MoneyResponse allocated,
            GoalPlan.Kind kind,
            String title,
            String detail,
            @Nullable MoneyResponse suggestedMonthly,
            @Nullable LocalDate suggestedDate,
            @Nullable MoneyResponse suggestedTarget) {}

    @Schema(name = "GoalMilestone")
    public record Milestone(int percent, MoneyResponse amount, boolean reached) {}

    @Schema(name = "GoalContribution")
    public record Contribution(
            UUID id,
            MoneyResponse amount,
            LocalDate contributedOn,
            @Nullable String note) {}
}
