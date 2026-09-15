package nz.kiwifinance.engine.insights;

import nz.kiwifinance.engine.analysis.CategoryRef;
import nz.kiwifinance.engine.money.Money;

/**
 * A plain-language observation about a person's money.
 *
 * @param key a stable identifier for this kind of insight, so clients can choose an icon
 * @param amount the headline amount, if the insight has one
 * @param category the category the insight is about, if any
 * @param action what the person can do about it, if anything
 */
public record Insight(
        String key, Tone tone, String title, String message, Money amount, CategoryRef category, Action action) {

    public Insight(String key, Tone tone, String title, String message, Money amount, CategoryRef category) {
        this(key, tone, title, message, amount, category, null);
    }

    /**
     * Where an insight leads. Clients decide how each one looks and where it goes.
     */
    public enum Action {
        OPEN_BUDGET,
        OPEN_GOALS,
        MOVE_MONEY,
        REVIEW_SPENDING,
        OPEN_EMERGENCY_FUND
    }

    public enum Tone {
        POSITIVE,
        NEUTRAL,
        WARNING
    }
}
