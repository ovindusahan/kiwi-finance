package nz.kiwifinance.engine.score;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Comparator;
import java.util.List;
import nz.kiwifinance.engine.money.Money;

/**
 * Combines savings, emergency fund, spending, budget, debt and goals into one score. Each part is
 * scored 0 to 100 and weighted; the weakest weighted part becomes the suggested next step.
 */
public final class KiwiScoreCalculator {

    private static final BigDecimal TARGET_SAVINGS_RATE = new BigDecimal("0.20");
    private static final BigDecimal DEBT_MONTHS_FOR_ZERO = BigDecimal.valueOf(3);

    public KiwiScore calculate(KiwiScoreRequest request) {
        List<KiwiScore.Component> components = List.of(
                savings(request),
                emergencyFund(request),
                spending(request),
                budget(request),
                debt(request),
                goals(request));
        int total = components.stream().mapToInt(c -> c.score() * c.weight()).sum() / 100;
        KiwiScore.Component weakest = components.stream()
                .min(Comparator.comparingInt((KiwiScore.Component c) -> (100 - c.score()) * c.weight())
                        .reversed())
                .orElseThrow();
        KiwiScore.Band band = KiwiScore.Band.of(total);
        return new KiwiScore(total, band, components, summary(band), weakest.tip());
    }

    private static KiwiScore.Component savings(KiwiScoreRequest request) {
        BigDecimal rate = request.savingsRate();
        int score =
                clamp(rate.divide(TARGET_SAVINGS_RATE, 4, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100)));
        String percent = percent(rate);
        return new KiwiScore.Component(
                "savings_rate",
                "Savings rate",
                score,
                25,
                rate.signum() < 0
                        ? "You spend more than you earn in a typical month."
                        : "You keep %s of your income.".formatted(percent),
                score >= 100
                        ? "Keep it up. Point your surplus at your goals."
                        : "Aim to keep 20% of your income. Start by setting up an automatic transfer on payday.");
    }

    private static KiwiScore.Component emergencyFund(KiwiScoreRequest request) {
        int score = clamp(request.emergencyFundProgress().multiply(BigDecimal.valueOf(100)));
        return new KiwiScore.Component(
                "emergency_fund",
                "Emergency fund",
                score,
                25,
                "Your emergency fund is %d%% of its target.".formatted(score),
                score >= 100
                        ? "Your safety net is in place. Top it up if your costs rise."
                        : "Build your emergency fund a little each pay. It protects every other goal.");
    }

    private static KiwiScore.Component spending(KiwiScoreRequest request) {
        int score = request.monthsAssessed() == 0 ? 50 : request.monthsWithSurplus() * 100 / request.monthsAssessed();
        return new KiwiScore.Component(
                "spending_balance",
                "Living within your means",
                score,
                15,
                request.monthsAssessed() == 0
                        ? "Not enough history yet."
                        : "You spent less than you earned in %d of the last %d months."
                                .formatted(request.monthsWithSurplus(), request.monthsAssessed()),
                "Look at your biggest lifestyle categories for easy wins.");
    }

    private static KiwiScore.Component budget(KiwiScoreRequest request) {
        if (request.budgetLines() == null || request.budgetLines() == 0) {
            return new KiwiScore.Component(
                    "budget",
                    "Budget",
                    40,
                    15,
                    "You don't have a budget yet.",
                    "Create a budget from your real spending in one tap.");
        }
        int score = request.budgetLinesOnTrack() * 100 / request.budgetLines();
        return new KiwiScore.Component(
                "budget",
                "Budget",
                score,
                15,
                "%d of %d budget categories stayed on track last month."
                        .formatted(request.budgetLinesOnTrack(), request.budgetLines()),
                "Adjust any category you go over every month so your budget stays realistic.");
    }

    private static KiwiScore.Component debt(KiwiScoreRequest request) {
        Money debt = request.consumerDebt();
        int score;
        if (!debt.isPositive()) {
            score = 100;
        } else if (!request.monthlyIncome().isPositive()) {
            score = 0;
        } else {
            BigDecimal months = debt.ratioOf(request.monthlyIncome());
            score = clamp(BigDecimal.ONE
                    .subtract(months.divide(DEBT_MONTHS_FOR_ZERO, 4, RoundingMode.HALF_UP))
                    .multiply(BigDecimal.valueOf(100)));
        }
        return new KiwiScore.Component(
                "debt",
                "Credit card and loan debt",
                score,
                10,
                debt.isPositive()
                        ? "You owe %s on cards and loans.".formatted(debt.formatWhole())
                        : "No credit card or loan debt.",
                debt.isPositive()
                        ? "Pay off the highest-interest debt first, then roll that payment into the next."
                        : "Stay debt-free by paying your credit card in full each month.");
    }

    private static KiwiScore.Component goals(KiwiScoreRequest request) {
        int score;
        String detail;
        if (request.activeGoals() == 0) {
            score = 40;
            detail = "You haven't set a goal yet.";
        } else {
            score = 50 + request.goalsOnTrack() * 50 / request.activeGoals();
            detail = "%d of %d goals %s on track."
                    .formatted(
                            request.goalsOnTrack(), request.activeGoals(), request.goalsOnTrack() == 1 ? "is" : "are");
        }
        return new KiwiScore.Component(
                "goals",
                "Goals",
                score,
                10,
                detail,
                request.activeGoals() == 0
                        ? "Set a goal, even a small one. Having a target makes saving easier."
                        : "Check the monthly amount each goal needs and adjust if you're behind.");
    }

    private static String summary(KiwiScore.Band band) {
        return switch (band) {
            case GETTING_STARTED -> "Every step counts. Start with a small emergency fund and a simple budget.";
            case BUILDING -> "You're building good habits. A few changes will lift your score quickly.";
            case SOLID -> "Your money is in good shape. Keep building your buffer and goals.";
            case THRIVING -> "You're thriving. Your foundations are strong.";
        };
    }

    private static int clamp(BigDecimal value) {
        return Math.max(0, Math.min(100, value.setScale(0, RoundingMode.HALF_UP).intValue()));
    }

    private static String percent(BigDecimal rate) {
        return rate.multiply(BigDecimal.valueOf(100)).setScale(0, RoundingMode.HALF_UP) + "%";
    }
}
