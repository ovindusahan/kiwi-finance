package nz.kiwifinance.engine.emergencyfund;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import nz.kiwifinance.engine.explain.Explanation;
import nz.kiwifinance.engine.money.Money;

/**
 * Sizes an emergency fund from a person's own essential spending. Three months of essentials is
 * the starting point; each factor that makes an income shock more likely or more damaging adds a
 * month, up to six.
 */
public final class EmergencyFundCalculator {

    static final int BASE_MONTHS = 3;
    static final int MAX_MONTHS = 6;
    private static final int BUILD_MONTHS = 12;
    private static final BigDecimal MAX_SHARE_OF_SURPLUS = new BigDecimal("0.5");
    private static final Money ROUNDING = Money.ofDollars(5);

    public EmergencyFundPlan plan(EmergencyFundRequest request) {
        List<String> reasons = new ArrayList<>();
        int months = BASE_MONTHS;
        if (request.variableIncome()) {
            months++;
            reasons.add("Your income changes from month to month.");
        }
        if (request.selfEmployed()) {
            months++;
            reasons.add("You are self-employed or on contract, with no notice period or redundancy pay.");
        }
        if (request.hasDependants()) {
            months++;
            reasons.add("Others depend on your income.");
        }
        if (request.singleIncomeHousehold()) {
            months++;
            reasons.add("Your household relies on one income.");
        }
        months = Math.min(months, MAX_MONTHS);

        Money essentials = request.monthlyEssentialSpending();
        Money target = essentials.times(months).roundUpTo(Money.ofDollars(100));
        Money current = Money.max(Money.ZERO, request.currentBalance());
        Money shortfall = Money.max(Money.ZERO, target.minus(current));
        BigDecimal progress = target.isPositive()
                ? current.ratioOf(target).min(BigDecimal.ONE).setScale(4, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;
        BigDecimal monthsCovered = essentials.isPositive()
                ? BigDecimal.valueOf(current.cents())
                        .divide(BigDecimal.valueOf(essentials.cents()), 1, RoundingMode.DOWN)
                : BigDecimal.ZERO;

        EmergencyFundPlan.Status status = shortfall.isZero() && target.isPositive()
                ? EmergencyFundPlan.Status.FUNDED
                : current.isPositive() ? EmergencyFundPlan.Status.BUILDING : EmergencyFundPlan.Status.NOT_STARTED;

        Money suggested = suggestedContribution(shortfall, request.monthlySurplus());
        Integer monthsToTarget = shortfall.isZero()
                ? Integer.valueOf(0)
                : suggested.isPositive() ? Math.toIntExact(ceilDiv(shortfall.cents(), suggested.cents())) : null;

        List<EmergencyFundPlan.Milestone> milestones = List.of(
                milestone("First $1,000", Money.ofDollars(1_000), current),
                milestone("One month of essentials", essentials.roundUpTo(Money.ofDollars(100)), current),
                milestone("Three months of essentials", essentials.times(3).roundUpTo(Money.ofDollars(100)), current),
                milestone("Fully funded", target, current));

        return new EmergencyFundPlan(
                essentials,
                months,
                target,
                current,
                shortfall,
                progress,
                monthsCovered,
                status,
                suggested,
                monthsToTarget,
                distinctByAmount(milestones, target),
                reasons,
                explain(request, months, target, current, shortfall, suggested, monthsToTarget, reasons));
    }

    /**
     * Keeps milestones up to the target, in ascending order. When two land on the same amount,
     * such as "three months" and "fully funded" for a three-month target, the later label wins.
     */
    private static List<EmergencyFundPlan.Milestone> distinctByAmount(
            List<EmergencyFundPlan.Milestone> milestones, Money target) {
        Map<Money, EmergencyFundPlan.Milestone> byAmount = new TreeMap<>();
        milestones.stream()
                .filter(m -> m.amount().isPositive() && !m.amount().isGreaterThan(target))
                .forEach(m -> byAmount.put(m.amount(), m));
        return List.copyOf(byAmount.values());
    }

    private static Money suggestedContribution(Money shortfall, Money surplus) {
        if (shortfall.isZero() || !surplus.isPositive()) {
            return Money.ZERO;
        }
        Money overAYear = shortfall.dividedBy(BUILD_MONTHS);
        Money affordable = surplus.times(MAX_SHARE_OF_SURPLUS);
        Money suggested = Money.min(overAYear, affordable).roundUpTo(ROUNDING);
        return Money.min(suggested, shortfall);
    }

    private static EmergencyFundPlan.Milestone milestone(String label, Money amount, Money current) {
        return new EmergencyFundPlan.Milestone(label, amount, current.isAtLeast(amount) && amount.isPositive());
    }

    private static long ceilDiv(long a, long b) {
        return -Math.floorDiv(-a, b);
    }

    private static Explanation explain(
            EmergencyFundRequest request,
            int months,
            Money target,
            Money current,
            Money shortfall,
            Money suggested,
            Integer monthsToTarget,
            List<String> reasons) {
        String summary;
        if (shortfall.isZero() && target.isPositive()) {
            summary = "Your emergency fund is fully funded with %s, enough for %d months of essentials."
                    .formatted(current.formatWhole(), months);
        } else if (monthsToTarget != null) {
            summary = "Aim for %s. Saving %s a month gets you there in about %d month%s."
                    .formatted(
                            target.formatWhole(),
                            suggested.formatWhole(),
                            monthsToTarget,
                            monthsToTarget == 1 ? "" : "s");
        } else {
            summary =
                    "Aim for %s. Right now your spending uses all your income, so start by freeing up even a small amount each week."
                            .formatted(target.formatWhole());
        }
        var builder = Explanation.builder()
                .summary(summary)
                .step(
                        "Essential spending",
                        request.monthlyEssentialSpending().format() + " a month",
                        "Rent or mortgage, power, groceries, transport, insurance and other costs that continue if your income stops.")
                .step(
                        "Months of cover",
                        String.valueOf(months),
                        reasons.isEmpty()
                                ? "Three months suits a steady salary with a partner or savings to fall back on."
                                : String.join(" ", reasons))
                .step("Target", target.format(), "Rounded up to the next $100.")
                .step("Saved so far", current.format());
        if (shortfall.isPositive()) {
            builder.step("Still to save", shortfall.format());
        }
        if (suggested.isPositive()) {
            builder.step(
                    "Suggested saving",
                    suggested.format() + " a month",
                    "Builds the fund within a year without using more than half of what you usually have left over.");
        }
        return builder.assumption(
                        "essentials_basis",
                        "Essential spending",
                        "Median of your last %d month%s."
                                .formatted(request.monthsOfData(), request.monthsOfData() == 1 ? "" : "s"),
                        null)
                .assumption(
                        "accounts",
                        "Emergency fund accounts",
                        "Only accounts you have marked for your emergency fund count. KiwiSaver and term deposits are excluded because you cannot reach them quickly.",
                        null)
                .build();
    }
}
