package nz.kiwifinance.engine.budgeting;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Comparator;
import java.util.List;
import nz.kiwifinance.engine.analysis.CategorySpending;
import nz.kiwifinance.engine.explain.Explanation;
import nz.kiwifinance.engine.money.Money;

/**
 * Builds a monthly budget from what a person actually spends. Essentials are budgeted at their
 * typical level. If that leaves too little to save, lifestyle categories are trimmed towards the
 * person's own lower-spending months, never below them, so the budget stays realistic.
 */
public final class BudgetRecommender {

    static final Money ROUNDING = Money.ofDollars(10);

    public BudgetRecommendation recommend(BudgetRequest request) {
        Money income = request.monthlyIncome();
        Money targetSavings = Money.max(income.times(request.targetSavingsRate()), request.goalContributions());

        List<Draft> drafts =
                request.spending().stream().map(BudgetRecommender::draft).toList();
        Money planned = Money.sum(drafts.stream().map(d -> d.recommended).toList());
        Money gap = targetSavings.minus(income.minus(planned));
        if (gap.isPositive()) {
            trim(drafts, gap);
            planned = Money.sum(drafts.stream().map(d -> d.recommended).toList());
        }

        List<BudgetRecommendation.Line> lines = drafts.stream()
                .sorted(Comparator.comparing((Draft d) -> d.kind)
                        .thenComparing(d -> d.recommended, Comparator.reverseOrder()))
                .map(Draft::toLine)
                .toList();
        Money savings = income.minus(planned);
        BigDecimal savingsRate =
                income.isPositive() ? savings.ratioOf(income).setScale(4, RoundingMode.HALF_UP) : BigDecimal.ZERO;
        boolean meetsTarget = savings.isAtLeast(targetSavings);

        return new BudgetRecommendation(
                income,
                lines,
                planned,
                savings,
                savingsRate,
                targetSavings,
                meetsTarget,
                explain(request, lines, planned, savings, targetSavings, meetsTarget));
    }

    private static Draft draft(CategorySpending spending) {
        // Irregular costs such as annual insurance have a low median but a meaningful average, so
        // the budget uses whichever is higher to spread them across the year.
        int months = Math.max(1, spending.monthly().size());
        Money average = spending.total().dividedBy(months);
        Money typical = Money.max(spending.monthlyMedian(), average);
        Money floor = Money.min(spending.monthlyLowerQuartile(), typical).roundUpTo(ROUNDING);
        BudgetRecommendation.Kind kind = spending.isUncategorised()
                ? BudgetRecommendation.Kind.UNCATEGORISED
                : spending.category().group().isEssential()
                        ? BudgetRecommendation.Kind.ESSENTIAL
                        : BudgetRecommendation.Kind.LIFESTYLE;
        return new Draft(spending, kind, typical, typical.roundUpTo(ROUNDING), floor);
    }

    private static void trim(List<Draft> drafts, Money gap) {
        List<Draft> trimmable = drafts.stream()
                .filter(d -> d.kind == BudgetRecommendation.Kind.LIFESTYLE && d.recommended.isGreaterThan(d.floor))
                .toList();
        Money capacity = Money.sum(
                trimmable.stream().map(d -> d.recommended.minus(d.floor)).toList());
        if (!capacity.isPositive()) {
            return;
        }
        Money toTrim = Money.min(gap, capacity);
        for (Draft draft : trimmable) {
            Money room = draft.recommended.minus(draft.floor);
            Money cut = toTrim.times(room.ratioOf(capacity), RoundingMode.CEILING);
            Money trimmed = draft.recommended.minus(Money.min(cut, room));
            draft.recommended = Money.max(draft.floor, roundDown(trimmed));
            draft.trimmed = true;
        }
    }

    private static Money roundDown(Money amount) {
        long remainder = Math.floorMod(amount.cents(), ROUNDING.cents());
        return Money.ofCents(amount.cents() - remainder);
    }

    private static Explanation explain(
            BudgetRequest request,
            List<BudgetRecommendation.Line> lines,
            Money planned,
            Money savings,
            Money targetSavings,
            boolean meetsTarget) {
        Money essentials = sumOf(lines, BudgetRecommendation.Kind.ESSENTIAL);
        Money lifestyle = sumOf(lines, BudgetRecommendation.Kind.LIFESTYLE);
        Money uncategorised = sumOf(lines, BudgetRecommendation.Kind.UNCATEGORISED);
        long trimmedCount =
                lines.stream().filter(BudgetRecommendation.Line::isTrimmed).count();

        String summary;
        if (!request.monthlyIncome().isPositive()) {
            summary = "We could not find any income yet, so this budget shows your typical spending only.";
        } else if (meetsTarget) {
            summary = "This budget leaves %s a month to save, which meets your target of %s."
                    .formatted(savings.formatWhole(), targetSavings.formatWhole());
        } else if (savings.isNegative()) {
            summary =
                    "Your typical spending is %s a month more than your income, even after trimming lifestyle spending."
                            .formatted(savings.negate().formatWhole());
        } else {
            summary =
                    "This budget leaves %s a month to save. That is short of your %s target, so look at essentials too."
                            .formatted(savings.formatWhole(), targetSavings.formatWhole());
        }

        var builder = Explanation.builder()
                .summary(summary)
                .step("Monthly income", request.monthlyIncome().format(), "Your typical take-home income per month.")
                .step(
                        "Essentials",
                        "- " + essentials.format(),
                        "Budgeted at what you usually spend, rounded up to the next $10.")
                .step(
                        "Lifestyle",
                        "- " + lifestyle.format(),
                        trimmedCount > 0
                                ? "%d categories trimmed towards your lower-spending months.".formatted(trimmedCount)
                                : "Budgeted at what you usually spend.");
        if (uncategorised.isPositive()) {
            builder.step(
                    "Uncategorised",
                    "- " + uncategorised.format(),
                    "Categorise these transactions to see where this money goes.");
        }
        return builder.step("Left to save", savings.format())
                .assumption(
                        "history",
                        "Spending history",
                        "Based on your last %d month%s of transactions."
                                .formatted(request.monthsOfData(), request.monthsOfData() == 1 ? "" : "s"),
                        null)
                .assumption(
                        "savings_target",
                        "Savings target",
                        "%s of income, or your goal contributions if those are higher."
                                .formatted(request.targetSavingsRate()
                                                .multiply(BigDecimal.valueOf(100))
                                                .stripTrailingZeros()
                                                .toPlainString()
                                        + "%"),
                        null)
                .build();
    }

    private static Money sumOf(List<BudgetRecommendation.Line> lines, BudgetRecommendation.Kind kind) {
        return Money.sum(lines.stream()
                .filter(l -> l.kind() == kind)
                .map(BudgetRecommendation.Line::recommended)
                .toList());
    }

    private static final class Draft {
        private final CategorySpending spending;
        private final BudgetRecommendation.Kind kind;
        private final Money typical;
        private final Money floor;
        private Money recommended;
        private boolean trimmed;

        private Draft(
                CategorySpending spending,
                BudgetRecommendation.Kind kind,
                Money typical,
                Money recommended,
                Money floor) {
            this.spending = spending;
            this.kind = kind;
            this.typical = typical;
            this.recommended = recommended;
            this.floor = floor;
        }

        private BudgetRecommendation.Line toLine() {
            String rationale = switch (kind) {
                case ESSENTIAL -> "You usually spend about %s a month.".formatted(typical.formatWhole());
                case UNCATEGORISED -> "An allowance for spending that has no category yet.";
                case LIFESTYLE ->
                    trimmed && recommended.isLessThan(typical.roundUpTo(ROUNDING))
                            ? "You usually spend about %s. In your lower-spending months it is closer to %s, so %s is realistic."
                                    .formatted(typical.formatWhole(), floor.formatWhole(), recommended.formatWhole())
                            : "You usually spend about %s a month.".formatted(typical.formatWhole());
            };
            return new BudgetRecommendation.Line(spending.category(), kind, typical, recommended, rationale);
        }
    }
}
