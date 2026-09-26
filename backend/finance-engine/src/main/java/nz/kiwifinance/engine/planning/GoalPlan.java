package nz.kiwifinance.engine.planning;

import java.time.LocalDate;
import java.util.List;
import nz.kiwifinance.engine.money.Money;

/**
 * @param available what the person usually has spare each month
 * @param emergencyFund the part kept for an emergency fund that is not yet funded
 * @param forGoals the part shared across goals
 * @param unplanned what is left after every goal has its share
 */
public record GoalPlan(
        Money available, Money emergencyFund, Money forGoals, Money unplanned, List<Advice> advice, String summary) {

    public GoalPlan {
        advice = List.copyOf(advice);
    }

    public enum Urgency {
        URGENT,
        SOON,
        LATER,
        FLEXIBLE
    }

    public enum Kind {
        ON_TRACK,
        SET_CONTRIBUTION,
        INCREASE_CONTRIBUTION,
        REDUCE_CONTRIBUTION,
        EXTEND_DATE,
        LOWER_TARGET,
        REVIEW_BUDGET
    }

    /**
     * One suggestion for one goal. The suggested values are what to apply to the goal, and are
     * {@code null} when the suggestion does not change them.
     *
     * @param allocated the goal's share of the money available each month
     */
    public record Advice(
            String goalId,
            Urgency urgency,
            Money allocated,
            Kind kind,
            String title,
            String detail,
            Money suggestedMonthly,
            LocalDate suggestedDate,
            Money suggestedTarget) {}
}
