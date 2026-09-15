package nz.kiwifinance.engine.planning;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.OptionalInt;
import nz.kiwifinance.engine.analysis.CategorySpending;
import nz.kiwifinance.engine.explain.Explanation;
import nz.kiwifinance.engine.money.Money;
import nz.kiwifinance.engine.time.PayFrequency;

/**
 * Answers "Can I afford this?", "By when?" and "What would I need to change?" from the person's
 * real position. The emergency fund and money already saved for other goals are protected:
 * a purchase is only affordable now if it fits in what is left after both.
 */
public final class AffordabilityCalculator {

    private static final DateTimeFormatter MONTH_YEAR = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.of("en", "NZ"));
    private static final Money MIN_CATEGORY_SAVING = Money.ofDollars(20);
    private static final int MAX_CATEGORY_LEVERS = 3;

    private final LoanCalculator loanCalculator = new LoanCalculator();

    public AffordabilityResult assess(AffordabilityRequest request) {
        var projector = new SavingsProjector(request.savingsInterestRate());
        PayFrequency frequency = request.payFrequency();

        Money liquid = Money.max(Money.ZERO, request.liquidBalance());
        Money afterGoals = Money.max(Money.ZERO, liquid.minus(request.reservedForGoals()));
        Money protectedForEmergencies = Money.min(request.emergencyFundTarget(), afterGoals);
        Money available = afterGoals.minus(protectedForEmergencies);
        Money capacity = Money.max(Money.ZERO, request.monthlySurplus().minus(request.monthlyGoalContributions()));

        LoanQuote loan = null;
        Money target = request.price();
        if (request.financing() != null) {
            var financing = request.financing();
            target = Money.min(financing.deposit(), request.price());
            Money borrowed = request.price().minus(target);
            if (borrowed.isPositive()) {
                loan = loanCalculator.quote(borrowed, financing.annualRate(), financing.termMonths(), financing.fees());
            }
        }
        boolean repaymentsFit = loan == null || !loan.monthlyRepayment().isGreaterThan(capacity);

        Money shortfall = Money.max(Money.ZERO, target.minus(available));
        OptionalInt months = projector.monthsToReach(target, available, capacity);
        Integer monthsNeeded = months.isPresent() ? months.getAsInt() : null;
        LocalDate realisticDate = monthsNeeded == null ? null : request.today().plusMonths(monthsNeeded);

        Money required = null;
        if (request.desiredDate() != null && shortfall.isPositive()) {
            int monthsUntil = (int) Math.max(0, ChronoUnit.MONTHS.between(request.today(), request.desiredDate()));
            required = projector.requiredMonthlyContribution(target, available, monthsUntil);
        }

        AffordabilityResult.Verdict verdict;
        if (shortfall.isZero()) {
            verdict = repaymentsFit
                    ? AffordabilityResult.Verdict.AFFORDABLE_NOW
                    : AffordabilityResult.Verdict.NEEDS_CHANGES;
        } else if (!capacity.isPositive()) {
            verdict = AffordabilityResult.Verdict.OUT_OF_REACH;
        } else if (request.desiredDate() != null) {
            verdict = !required.isGreaterThan(capacity) && repaymentsFit
                    ? AffordabilityResult.Verdict.ON_TRACK
                    : AffordabilityResult.Verdict.NEEDS_CHANGES;
        } else {
            verdict = monthsNeeded != null && repaymentsFit
                    ? AffordabilityResult.Verdict.SAVE_UP
                    : AffordabilityResult.Verdict.NEEDS_CHANGES;
        }

        Money planMonthly = required != null ? required : shortfall.isPositive() ? capacity : Money.ZERO;
        Money perPeriod = PayFrequency.MONTHLY.convert(planMonthly, frequency);

        List<AffordabilityResult.Lever> levers = verdict == AffordabilityResult.Verdict.AFFORDABLE_NOW
                        || verdict == AffordabilityResult.Verdict.ON_TRACK
                ? List.of()
                : levers(request, projector, target, available, capacity, required, monthsNeeded, realisticDate);

        List<String> warnings = warnings(request, liquid, target, available, capacity, loan);
        String headline = headline(request, verdict, target, available, capacity, required, realisticDate, loan);

        return new AffordabilityResult(
                verdict,
                headline,
                request.price(),
                target,
                available,
                shortfall,
                capacity,
                monthsNeeded,
                realisticDate,
                request.desiredDate(),
                required,
                perPeriod,
                frequency,
                loan,
                levers,
                warnings,
                explain(
                        request,
                        liquid,
                        protectedForEmergencies,
                        available,
                        capacity,
                        target,
                        shortfall,
                        monthsNeeded,
                        loan));
    }

    private static List<AffordabilityResult.Lever> levers(
            AffordabilityRequest request,
            SavingsProjector projector,
            Money target,
            Money available,
            Money capacity,
            Money required,
            Integer monthsNeeded,
            LocalDate realisticDate) {
        List<AffordabilityResult.Lever> levers = new ArrayList<>();
        PayFrequency frequency = request.payFrequency();
        LocalDate today = request.today();

        if (required != null && required.isGreaterThan(capacity)) {
            Money extra = required.minus(capacity);
            Money perPeriod = PayFrequency.MONTHLY.convert(extra, frequency);
            levers.add(new AffordabilityResult.Lever(
                    AffordabilityResult.Lever.Kind.SAVE_MORE,
                    "Save %s more a %s".formatted(perPeriod.formatWhole(), frequency.periodName()),
                    "Putting aside %s a %s in total gets you there by %s."
                            .formatted(
                                    PayFrequency.MONTHLY
                                            .convert(required, frequency)
                                            .formatWhole(),
                                    frequency.periodName(),
                                    MONTH_YEAR.format(request.desiredDate())),
                    extra,
                    perPeriod,
                    request.desiredDate(),
                    null));
        }

        List<CategorySpending> cuttable = request.lifestyleSpending().stream()
                .filter(s -> !s.isUncategorised())
                .filter(s -> s.monthlyMedian().minus(s.monthlyLowerQuartile()).isAtLeast(MIN_CATEGORY_SAVING))
                .sorted(Comparator.comparing(
                                (CategorySpending s) -> s.monthlyMedian().minus(s.monthlyLowerQuartile()))
                        .reversed())
                .limit(MAX_CATEGORY_LEVERS)
                .toList();
        Money combinedCut = Money.ZERO;
        for (CategorySpending spending : cuttable) {
            Money cut = spending.monthlyMedian()
                    .minus(spending.monthlyLowerQuartile())
                    .roundUpTo(Money.ofDollars(5));
            combinedCut = combinedCut.plus(cut);
            OptionalInt newMonths = projector.monthsToReach(target, available, capacity.plus(cut));
            levers.add(new AffordabilityResult.Lever(
                    AffordabilityResult.Lever.Kind.REDUCE_SPENDING,
                    "Spend %s less on %s"
                            .formatted(
                                    PayFrequency.MONTHLY.convert(cut, frequency).formatWhole(),
                                    spending.category().name()),
                    "You usually spend %s a month on %s, but some months only %s. Matching your lower months frees up %s a month."
                            .formatted(
                                    spending.monthlyMedian().formatWhole(),
                                    spending.category().name(),
                                    spending.monthlyLowerQuartile().formatWhole(),
                                    cut.formatWhole()),
                    cut,
                    PayFrequency.MONTHLY.convert(cut, frequency),
                    dateAfter(today, newMonths),
                    monthsSooner(monthsNeeded, newMonths)));
        }
        if (cuttable.size() > 1) {
            OptionalInt newMonths = projector.monthsToReach(target, available, capacity.plus(combinedCut));
            levers.add(new AffordabilityResult.Lever(
                    AffordabilityResult.Lever.Kind.REDUCE_SPENDING,
                    "Make all %d changes".formatted(cuttable.size()),
                    "Trimming each of these categories together frees up %s a month."
                            .formatted(combinedCut.formatWhole()),
                    combinedCut,
                    PayFrequency.MONTHLY.convert(combinedCut, frequency),
                    dateAfter(today, newMonths),
                    monthsSooner(monthsNeeded, newMonths)));
        }

        if (request.monthlyGoalContributions().isPositive()) {
            OptionalInt newMonths =
                    projector.monthsToReach(target, available, capacity.plus(request.monthlyGoalContributions()));
            levers.add(new AffordabilityResult.Lever(
                    AffordabilityResult.Lever.Kind.PAUSE_GOALS,
                    "Pause your other goals",
                    "Redirecting the %s a month you put towards other goals would speed this up, but delays those goals."
                            .formatted(request.monthlyGoalContributions().formatWhole()),
                    request.monthlyGoalContributions(),
                    PayFrequency.MONTHLY.convert(request.monthlyGoalContributions(), frequency),
                    dateAfter(today, newMonths),
                    monthsSooner(monthsNeeded, newMonths)));
        }

        Money kiwiSaver = request.kiwiSaverFirstHomeAvailable();
        if (kiwiSaver != null && kiwiSaver.isPositive()) {
            OptionalInt newMonths = projector.monthsToReach(target, available.plus(kiwiSaver), capacity);
            levers.add(new AffordabilityResult.Lever(
                    AffordabilityResult.Lever.Kind.USE_KIWISAVER,
                    "Use your KiwiSaver first-home withdrawal",
                    "You could withdraw about %s from KiwiSaver towards your first home, keeping the required $1,000 in the account."
                            .formatted(kiwiSaver.formatWhole()),
                    null,
                    null,
                    dateAfter(today, newMonths),
                    monthsSooner(monthsNeeded, newMonths)));
        }

        if (request.desiredDate() != null && realisticDate != null && realisticDate.isAfter(request.desiredDate())) {
            levers.add(new AffordabilityResult.Lever(
                    AffordabilityResult.Lever.Kind.MOVE_DATE,
                    "Aim for %s instead".formatted(MONTH_YEAR.format(realisticDate)),
                    "Keep your current spending and savings, and plan the purchase for when you will have the money.",
                    null,
                    null,
                    realisticDate,
                    null));
        }
        return levers;
    }

    private static LocalDate dateAfter(LocalDate today, OptionalInt months) {
        return months.isPresent() ? today.plusMonths(months.getAsInt()) : null;
    }

    private static Integer monthsSooner(Integer current, OptionalInt improved) {
        if (current == null || improved.isEmpty()) {
            return null;
        }
        return Math.max(0, current - improved.getAsInt());
    }

    private static List<String> warnings(
            AffordabilityRequest request, Money liquid, Money target, Money available, Money capacity, LoanQuote loan) {
        List<String> warnings = new ArrayList<>();
        if (available.isLessThan(target)
                && liquid.minus(request.reservedForGoals()).isAtLeast(target)) {
            warnings.add("You could pay for this today, but only by using your emergency fund. We don't recommend it.");
        }
        if (loan != null && loan.monthlyRepayment().isGreaterThan(capacity)) {
            warnings.add("Repayments of %s a month are more than the %s you usually have left over."
                    .formatted(loan.monthlyRepayment().formatWhole(), capacity.formatWhole()));
        }
        if (request.monthsOfData() < 2) {
            warnings.add("We only have %d month%s of your transactions, so these figures will firm up as more arrive."
                    .formatted(request.monthsOfData(), request.monthsOfData() == 1 ? "" : "s"));
        }
        return warnings;
    }

    private static String headline(
            AffordabilityRequest request,
            AffordabilityResult.Verdict verdict,
            Money target,
            Money available,
            Money capacity,
            Money required,
            LocalDate realisticDate,
            LoanQuote loan) {
        String item = request.itemName();
        PayFrequency frequency = request.payFrequency();
        return switch (verdict) {
            case AFFORDABLE_NOW ->
                loan == null
                        ? "Yes, you can afford %s now and still have %s left over."
                                .formatted(item, available.minus(target).formatWhole())
                        : "Yes, you have the %s deposit for %s and the repayments fit your budget."
                                .formatted(target.formatWhole(), item);
            case ON_TRACK ->
                "Yes, you can have %s by %s if you put aside %s a %s."
                        .formatted(
                                item,
                                MONTH_YEAR.format(request.desiredDate()),
                                PayFrequency.MONTHLY
                                        .convert(required, frequency)
                                        .formatWhole(),
                                frequency.periodName());
            case SAVE_UP ->
                "You could afford %s by %s by saving %s a %s."
                        .formatted(
                                item,
                                MONTH_YEAR.format(realisticDate),
                                PayFrequency.MONTHLY
                                        .convert(capacity, frequency)
                                        .formatWhole(),
                                frequency.periodName());
            case NEEDS_CHANGES -> {
                if (loan != null && loan.monthlyRepayment().isGreaterThan(capacity)) {
                    yield "The repayments on %s would stretch your budget, so you'd need to free up money first."
                            .formatted(item);
                }
                if (request.desiredDate() != null && realisticDate != null) {
                    yield "Not by %s at your current pace. You could have %s by %s, or sooner with a few changes."
                            .formatted(
                                    MONTH_YEAR.format(request.desiredDate()), item, MONTH_YEAR.format(realisticDate));
                }
                yield "%s is a stretch right now, but these changes can get you there.".formatted(capitalise(item));
            }
            case OUT_OF_REACH ->
                request.monthlySurplus().isPositive()
                        ? "Everything you have spare each month is already going to your other goals. Here's how to make room for %s."
                                .formatted(item)
                        : "Right now nothing is left over each month to save towards %s. Here's how to change that."
                                .formatted(item);
        };
    }

    private static String capitalise(String text) {
        return text.isEmpty() ? text : Character.toUpperCase(text.charAt(0)) + text.substring(1);
    }

    private static Explanation explain(
            AffordabilityRequest request,
            Money liquid,
            Money protectedForEmergencies,
            Money available,
            Money capacity,
            Money target,
            Money shortfall,
            Integer monthsNeeded,
            LoanQuote loan) {
        var builder = Explanation.builder()
                .summary("We compared what you can use today and what you can save each month with the cost of %s."
                        .formatted(request.itemName()))
                .step("Money in your everyday and savings accounts", liquid.format());
        if (request.reservedForGoals().isPositive()) {
            builder.step(
                    "Already saved for other goals",
                    "- " + request.reservedForGoals().format(),
                    "Kept for those goals.");
        }
        builder.step(
                        "Kept for emergencies",
                        "- " + protectedForEmergencies.format(),
                        "Your emergency fund is never used for planned purchases.")
                .step("Available now", available.format())
                .step(
                        loan == null ? "Price" : "Deposit needed",
                        target.format(),
                        loan == null
                                ? null
                                : "The rest, %s, is borrowed."
                                        .formatted(loan.principal().format()))
                .step(
                        "Usually left over each month",
                        Money.max(Money.ZERO, request.monthlySurplus()).format(),
                        "Your typical take-home income minus your typical spending.");
        if (request.monthlyGoalContributions().isPositive()) {
            builder.step(
                    "Already going to other goals",
                    "- " + request.monthlyGoalContributions().format());
        }
        builder.step("Free to save towards this", capacity.format() + " a month");
        if (shortfall.isPositive()) {
            builder.step("Still to save", shortfall.format());
            builder.step(
                    "Time needed at this pace",
                    monthsNeeded == null
                            ? "Not reachable without changes"
                            : monthsNeeded + (monthsNeeded == 1 ? " month" : " months"));
        }
        if (loan != null) {
            builder.step(
                    "Loan repayments",
                    loan.monthlyRepayment().format() + " a month",
                    loan.explanation().summary());
        }
        return builder.assumption(
                        "spending_basis",
                        "Typical month",
                        "Medians of your last %d month%s of income and spending."
                                .formatted(request.monthsOfData(), request.monthsOfData() == 1 ? "" : "s"),
                        null)
                .assumption(
                        "interest",
                        "Interest on savings",
                        LoanCalculator.percent(request.savingsInterestRate()) + " a year after tax, added monthly.",
                        null)
                .build();
    }
}
