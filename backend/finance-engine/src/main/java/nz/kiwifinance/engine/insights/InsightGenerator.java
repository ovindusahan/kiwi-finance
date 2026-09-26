package nz.kiwifinance.engine.insights;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import nz.kiwifinance.engine.analysis.CashflowAnalysis;
import nz.kiwifinance.engine.analysis.CategorySpending;
import nz.kiwifinance.engine.analysis.RecurringPayment;
import nz.kiwifinance.engine.emergencyfund.EmergencyFundPlan;
import nz.kiwifinance.engine.money.Money;

/**
 * Turns analysis into a short, prioritised list of things worth knowing. Warnings come first,
 * then neutral observations, then encouragement.
 */
public final class InsightGenerator {

    private static final BigDecimal SPIKE_RATIO = new BigDecimal("1.3");
    private static final Money MIN_SPIKE = Money.ofDollars(50);
    private static final BigDecimal STRONG_SAVINGS_RATE = new BigDecimal("0.2");
    private static final BigDecimal LOW_SAVINGS_RATE = new BigDecimal("0.05");
    private static final BigDecimal HIGH_UNCATEGORISED_SHARE = new BigDecimal("0.15");
    private static final Money MIN_SUBSCRIPTIONS_PER_MONTH = Money.ofDollars(30);
    private static final int MAX_SPIKES = 2;

    public List<Insight> generate(InsightRequest request) {
        CashflowAnalysis cashflow = request.cashflow();
        List<Insight> insights = new ArrayList<>();
        if (cashflow.monthsOfData() == 0) {
            insights.add(new Insight(
                    "getting_started",
                    Insight.Tone.NEUTRAL,
                    "Connect your bank to see insights",
                    "Once your transactions arrive we'll show where your money goes and what you can change.",
                    null,
                    null));
            return insights;
        }

        PersonalInsights.add(request, insights);
        savings(cashflow, insights);
        spikes(request.spending(), insights);
        subscriptions(request.recurring(), insights);
        uncategorised(request.spending(), insights);
        emergencyFund(request.emergencyFund(), insights);
        if (cashflow.hasVariableIncome()) {
            insights.add(new Insight(
                    "variable_income",
                    Insight.Tone.NEUTRAL,
                    "Your income varies",
                    "Your income changes noticeably from month to month, so we plan using your typical month and suggest a larger emergency fund.",
                    cashflow.typicalIncome(),
                    null));
        }
        topLifestyleCategory(request.spending(), insights);

        insights.sort(Comparator.comparing(Insight::tone, Comparator.comparingInt(InsightGenerator::rank)));
        return insights.stream().map(InsightGenerator::withDefaultAction).toList();
    }

    /**
     * Gives the general insights somewhere to go, so every insight leads to something to do.
     */
    private static Insight withDefaultAction(Insight insight) {
        if (insight.action() != null) {
            return insight;
        }
        Insight.Action action = switch (insight.key()) {
            case "category_spike", "subscriptions", "top_lifestyle_category", "uncategorised" ->
                Insight.Action.REVIEW_SPENDING;
            case "low_savings_rate", "spending_exceeds_income" -> Insight.Action.OPEN_BUDGET;
            case "emergency_fund_missing", "emergency_fund_low", "emergency_fund_building" ->
                Insight.Action.OPEN_EMERGENCY_FUND;
            default -> null;
        };
        return action == null
                ? insight
                : new Insight(
                        insight.key(),
                        insight.tone(),
                        insight.title(),
                        insight.message(),
                        insight.amount(),
                        insight.category(),
                        action);
    }

    private static int rank(Insight.Tone tone) {
        return switch (tone) {
            case WARNING -> 0;
            case NEUTRAL -> 1;
            case POSITIVE -> 2;
        };
    }

    private static void savings(CashflowAnalysis cashflow, List<Insight> insights) {
        Money income = cashflow.typicalIncome();
        Money surplus = cashflow.typicalSurplus();
        if (!income.isPositive()) {
            return;
        }
        if (surplus.isNegative()) {
            insights.add(new Insight(
                    "spending_exceeds_income",
                    Insight.Tone.WARNING,
                    "You're spending more than you earn",
                    "In a typical month you spend %s more than comes in. The budget page shows where to start."
                            .formatted(surplus.negate().formatWhole()),
                    surplus.negate(),
                    null));
            return;
        }
        BigDecimal rate = surplus.ratioOf(income);
        String percent = rate.multiply(BigDecimal.valueOf(100)).setScale(0, RoundingMode.HALF_UP) + "%";
        if (rate.compareTo(STRONG_SAVINGS_RATE) >= 0) {
            insights.add(new Insight(
                    "strong_savings_rate",
                    Insight.Tone.POSITIVE,
                    "You keep %s of your income".formatted(percent),
                    "That's %s a month left over in a typical month. Great work.".formatted(surplus.formatWhole()),
                    surplus,
                    null));
        } else if (rate.compareTo(LOW_SAVINGS_RATE) < 0) {
            insights.add(new Insight(
                    "low_savings_rate",
                    Insight.Tone.WARNING,
                    "Only %s of your income is left over".formatted(percent),
                    "A typical month leaves %s. Even a small regular saving builds a buffer for surprises."
                            .formatted(surplus.formatWhole()),
                    surplus,
                    null));
        } else {
            insights.add(new Insight(
                    "savings_rate",
                    Insight.Tone.NEUTRAL,
                    "You keep %s of your income".formatted(percent),
                    "A typical month leaves %s after spending. Aiming for 20%% would speed up your goals."
                            .formatted(surplus.formatWhole()),
                    surplus,
                    null));
        }
    }

    private static void spikes(List<CategorySpending> spending, List<Insight> insights) {
        spending.stream()
                .filter(s -> !s.isUncategorised() && s.monthly().size() >= 3)
                .filter(s -> {
                    Money latest = s.latestMonth();
                    Money median = s.monthlyMedian();
                    return median.isPositive()
                            && latest.isGreaterThan(median.times(SPIKE_RATIO))
                            && latest.minus(median).isAtLeast(MIN_SPIKE);
                })
                .sorted(Comparator.comparing(
                                (CategorySpending s) -> s.latestMonth().minus(s.monthlyMedian()))
                        .reversed())
                .limit(MAX_SPIKES)
                .forEach(s -> {
                    Money extra = s.latestMonth().minus(s.monthlyMedian());
                    insights.add(new Insight(
                            "category_spike",
                            s.category().group().isEssential() ? Insight.Tone.NEUTRAL : Insight.Tone.WARNING,
                            "%s was higher than usual".formatted(s.category().name()),
                            "You spent %s on %s last month, %s more than a typical month."
                                    .formatted(
                                            s.latestMonth().formatWhole(),
                                            s.category().name(),
                                            extra.formatWhole()),
                            extra,
                            s.category()));
                });
    }

    private static void subscriptions(List<RecurringPayment> recurring, List<Insight> insights) {
        List<RecurringPayment> subscriptions = recurring.stream()
                .filter(r -> !r.incoming() && r.subscription())
                .toList();
        Money monthly = Money.sum(
                subscriptions.stream().map(RecurringPayment::monthlyAmount).toList());
        if (subscriptions.isEmpty() || monthly.isLessThan(MIN_SUBSCRIPTIONS_PER_MONTH)) {
            return;
        }
        Money annual = Money.sum(
                subscriptions.stream().map(RecurringPayment::annualAmount).toList());
        insights.add(new Insight(
                "subscriptions",
                Insight.Tone.NEUTRAL,
                "%d subscription%s cost %s a year"
                        .formatted(subscriptions.size(), subscriptions.size() == 1 ? "" : "s", annual.formatWhole()),
                "That's about %s a month. Check you still use each one.".formatted(monthly.formatWhole()),
                annual,
                null));
    }

    private static void uncategorised(List<CategorySpending> spending, List<Insight> insights) {
        spending.stream()
                .filter(CategorySpending::isUncategorised)
                .filter(s -> s.share().compareTo(HIGH_UNCATEGORISED_SHARE) > 0)
                .findFirst()
                .ifPresent(s -> insights.add(new Insight(
                        "uncategorised",
                        Insight.Tone.NEUTRAL,
                        "Some spending has no category",
                        "%s of your spending isn't categorised yet. Categorising it makes your budget and insights more accurate."
                                .formatted(s.share()
                                                .multiply(BigDecimal.valueOf(100))
                                                .setScale(0, RoundingMode.HALF_UP) + "%"),
                        s.total(),
                        null)));
    }

    private static void emergencyFund(EmergencyFundPlan plan, List<Insight> insights) {
        if (plan == null || !plan.target().isPositive()) {
            return;
        }
        switch (plan.status()) {
            case FUNDED ->
                insights.add(new Insight(
                        "emergency_fund_funded",
                        Insight.Tone.POSITIVE,
                        "Your emergency fund is fully funded",
                        "You have %s set aside, enough for %d months of essentials."
                                .formatted(plan.current().formatWhole(), plan.targetMonths()),
                        plan.current(),
                        null));
            case NOT_STARTED ->
                insights.add(new Insight(
                        "emergency_fund_missing",
                        Insight.Tone.WARNING,
                        "Start an emergency fund",
                        "A buffer of %s would cover %d months of essentials if your income stopped."
                                .formatted(plan.target().formatWhole(), plan.targetMonths()),
                        plan.target(),
                        null));
            case BUILDING -> {
                if (plan.monthsCovered().compareTo(BigDecimal.ONE) < 0) {
                    insights.add(new Insight(
                            "emergency_fund_low",
                            Insight.Tone.WARNING,
                            "Your emergency fund covers less than a month",
                            "Building it to %s should come before big purchases."
                                    .formatted(plan.target().formatWhole()),
                            plan.shortfall(),
                            null));
                } else {
                    insights.add(new Insight(
                            "emergency_fund_building",
                            Insight.Tone.POSITIVE,
                            "Your emergency fund covers %s months"
                                    .formatted(plan.monthsCovered()
                                            .stripTrailingZeros()
                                            .toPlainString()),
                            "%s to go until you reach %s."
                                    .formatted(
                                            plan.shortfall().formatWhole(),
                                            plan.target().formatWhole()),
                            plan.shortfall(),
                            null));
                }
            }
        }
    }

    private static void topLifestyleCategory(List<CategorySpending> spending, List<Insight> insights) {
        spending.stream()
                .filter(s -> !s.isUncategorised() && !s.category().group().isEssential())
                .max(Comparator.comparing(CategorySpending::monthlyMedian))
                .filter(s -> s.monthlyMedian().isPositive())
                .ifPresent(s -> insights.add(new Insight(
                        "top_lifestyle_category",
                        Insight.Tone.NEUTRAL,
                        "%s is your biggest lifestyle cost"
                                .formatted(s.category().name()),
                        "You usually spend %s a month on %s. Small changes here make the biggest difference."
                                .formatted(
                                        s.monthlyMedian().formatWhole(),
                                        s.category().name()),
                        s.monthlyMedian(),
                        s.category())));
    }
}
