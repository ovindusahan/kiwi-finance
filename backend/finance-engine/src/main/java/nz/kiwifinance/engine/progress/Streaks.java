package nz.kiwifinance.engine.progress;

import org.jspecify.annotations.Nullable;

/**
 * Habit streaks, counted back from the most recent complete period.
 *
 * @param noSpendDays consecutive days without lifestyle spending, ending yesterday
 * @param noSpendDaysThisMonth days this month without lifestyle spending, up to yesterday
 * @param surplusMonths consecutive complete months where income exceeded spending
 * @param underBudgetWeeks consecutive complete weeks of lifestyle spending within budget, or
 *     {@code null} without a budget
 */
public record Streaks(
        int noSpendDays,
        int noSpendDaysThisMonth,
        int surplusMonths,
        @Nullable Integer underBudgetWeeks) {}
