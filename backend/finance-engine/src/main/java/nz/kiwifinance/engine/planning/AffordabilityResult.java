package nz.kiwifinance.engine.planning;

import java.time.LocalDate;
import java.util.List;
import nz.kiwifinance.engine.explain.Explanation;
import nz.kiwifinance.engine.money.Money;
import nz.kiwifinance.engine.time.PayFrequency;

/**
 * @param target the amount to have saved: the full price, or the deposit when financing
 * @param monthsNeeded months to reach the target at the current pace, or {@code null} if it is not
 *     reachable without changes
 * @param requiredMonthlySaving the saving needed to reach the target by the desired date, or
 *     {@code null} when no date was given
 */
public record AffordabilityResult(
        Verdict verdict,
        String headline,
        Money price,
        Money target,
        Money availableNow,
        Money shortfall,
        Money monthlySavingCapacity,
        Integer monthsNeeded,
        LocalDate realisticDate,
        LocalDate desiredDate,
        Money requiredMonthlySaving,
        Money requiredPerPayPeriod,
        PayFrequency payFrequency,
        LoanQuote loan,
        List<Lever> levers,
        List<String> warnings,
        Explanation explanation) {

    public AffordabilityResult {
        levers = List.copyOf(levers);
        warnings = List.copyOf(warnings);
    }

    public enum Verdict {
        /** Affordable today without touching the emergency fund or other goals. */
        AFFORDABLE_NOW,
        /** Reachable by the desired date at the current pace. */
        ON_TRACK,
        /** Not reachable by the desired date without changes. */
        NEEDS_CHANGES,
        /** No date given; reachable by saving at the current pace. */
        SAVE_UP,
        /** Nothing is left over each month, so it cannot be saved for without changes. */
        OUT_OF_REACH
    }

    /**
     * A concrete change that brings the purchase closer.
     *
     * @param monthlyAmount the monthly effect of the change, when it has one
     * @param resultingDate when the target would be reached with this change, if reachable
     */
    public record Lever(
            Kind kind,
            String title,
            String description,
            Money monthlyAmount,
            Money perPayPeriod,
            LocalDate resultingDate,
            Integer monthsSooner) {

        public enum Kind {
            SAVE_MORE,
            REDUCE_SPENDING,
            PAUSE_GOALS,
            USE_KIWISAVER,
            MOVE_DATE
        }
    }
}
