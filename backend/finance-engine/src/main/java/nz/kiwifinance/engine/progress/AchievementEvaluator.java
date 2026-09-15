package nz.kiwifinance.engine.progress;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import nz.kiwifinance.engine.money.Money;

/**
 * Evaluates every achievement from the person's current situation. Achievements are derived
 * rather than stored, so they always reflect reality.
 */
public final class AchievementEvaluator {

    private static final Money FIRST_THOUSAND = Money.ofDollars(1_000);

    public List<Achievement> evaluate(AchievementFacts facts) {
        Streaks streaks = facts.streaks();
        return List.of(
                flag(
                        "first_steps",
                        "First steps",
                        "Bring in your first transactions.",
                        "sprout",
                        facts.hasTransactions()),
                flag("connected", "Plugged in", "Connect your bank through Akahu.", "plug", facts.hasBankFeed()),
                flag("budgeter", "Budgeter", "Create your first budget.", "clipboard-check", facts.hasBudget()),
                flag(
                        "under_budget",
                        "Under budget",
                        "Finish a month within your budget.",
                        "trophy",
                        facts.lastMonthUnderBudget()),
                amount(
                        "first_thousand",
                        "First $1,000",
                        "Save $1,000 in your emergency fund.",
                        "piggy-bank",
                        facts.emergencyFund(),
                        FIRST_THOUSAND),
                amount(
                        "one_month_buffer",
                        "One month buffer",
                        "Cover a month of essential costs.",
                        "shield",
                        facts.emergencyFund(),
                        facts.monthlyEssentials()),
                ratio(
                        "safety_net",
                        "Safety net",
                        "Fully fund your emergency fund.",
                        "shield-check",
                        facts.emergencyFundProgress()),
                count("goal_setter", "Goal setter", "Set your first savings goal.", "target", facts.goalsCreated(), 1),
                count("goal_getter", "Goal getter", "Reach a savings goal.", "party-popper", facts.goalsAchieved(), 1),
                ratio(
                        "super_saver",
                        "Super saver",
                        "Keep 20% of your income in a typical month.",
                        "rocket",
                        facts.savingsRate()
                                .max(BigDecimal.ZERO)
                                .divide(new BigDecimal("0.20"), 4, RoundingMode.HALF_UP)),
                count(
                        "no_spend_week",
                        "No-spend week",
                        "Go seven days without lifestyle spending.",
                        "flame",
                        streaks.noSpendDays(),
                        7),
                count(
                        "steady_saver",
                        "Steady saver",
                        "Spend less than you earn three months in a row.",
                        "calendar-check",
                        streaks.surplusMonths(),
                        3),
                flag(
                        "tidy",
                        "Tidy books",
                        "Have every transaction categorised.",
                        "sparkles",
                        facts.hasTransactions() && facts.uncategorisedTransactions() == 0));
    }

    private static Achievement flag(String key, String title, String description, String icon, boolean unlocked) {
        return new Achievement(key, title, description, icon, unlocked, unlocked ? BigDecimal.ONE : BigDecimal.ZERO);
    }

    private static Achievement amount(
            String key, String title, String description, String icon, Money current, Money target) {
        if (!target.isPositive()) {
            return new Achievement(key, title, description, icon, false, BigDecimal.ZERO);
        }
        return ratio(key, title, description, icon, current.ratioOf(target));
    }

    private static Achievement count(
            String key, String title, String description, String icon, int current, int target) {
        return ratio(
                key,
                title,
                description,
                icon,
                BigDecimal.valueOf(current).divide(BigDecimal.valueOf(target), 4, RoundingMode.HALF_UP));
    }

    private static Achievement ratio(String key, String title, String description, String icon, BigDecimal progress) {
        BigDecimal clamped = progress.max(BigDecimal.ZERO).min(BigDecimal.ONE).setScale(2, RoundingMode.DOWN);
        return new Achievement(key, title, description, icon, progress.compareTo(BigDecimal.ONE) >= 0, clamped);
    }
}
