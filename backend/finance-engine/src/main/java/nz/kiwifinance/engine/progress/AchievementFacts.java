package nz.kiwifinance.engine.progress;

import java.math.BigDecimal;
import nz.kiwifinance.engine.money.Money;

/**
 * What the achievement rules need to know about a person.
 */
public record AchievementFacts(
        boolean hasTransactions,
        boolean hasBankFeed,
        boolean hasBudget,
        boolean lastMonthUnderBudget,
        Money emergencyFund,
        Money monthlyEssentials,
        BigDecimal emergencyFundProgress,
        int goalsCreated,
        int goalsAchieved,
        BigDecimal savingsRate,
        int uncategorisedTransactions,
        Streaks streaks) {}
