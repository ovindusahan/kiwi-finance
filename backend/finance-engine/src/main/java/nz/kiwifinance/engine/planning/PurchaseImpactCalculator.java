package nz.kiwifinance.engine.planning;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import nz.kiwifinance.engine.money.Money;

/**
 * Works out what buying a car, a house or another big item would really cost: the deposit, the
 * loan, one-off and running costs, and what is left of the person's monthly budget afterwards.
 * Typical New Zealand figures fill in anything the person leaves blank, and every assumption is
 * spelled out in the notes.
 */
public final class PurchaseImpactCalculator {

    static final BigDecimal CAR_RATE = new BigDecimal("0.0995");
    static final BigDecimal HOUSE_RATE = new BigDecimal("0.0549");
    static final BigDecimal OTHER_RATE = new BigDecimal("0.1295");
    /** What banks typically add to a home loan rate when the deposit is under 20%. */
    static final BigDecimal LOW_EQUITY_MARGIN = new BigDecimal("0.0050");

    static final BigDecimal STANDARD_DEPOSIT = new BigDecimal("0.20");
    static final BigDecimal FIRST_HOME_DEPOSIT = new BigDecimal("0.05");
    static final Money CAR_LOAN_FEES = Money.ofDollars(250);
    /** KiwiSaver members must leave this much in their account after a first-home withdrawal. */
    static final Money KIWISAVER_MINIMUM = Money.ofDollars(1_000);

    private static final DateTimeFormatter MONTH_YEAR = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.of("en", "NZ"));
    private static final BigDecimal COMFORTABLE_SURPLUS = new BigDecimal("0.10");
    private static final BigDecimal STRETCH_SHORTFALL = new BigDecimal("-0.05");
    private static final BigDecimal HOUSE_COST_LIMIT = new BigDecimal("0.35");
    private static final BigDecimal CAR_COST_LIMIT = new BigDecimal("0.15");

    private final LoanCalculator loans = new LoanCalculator();

    public PurchaseImpact assess(PurchaseImpactRequest request) {
        PurchaseImpactRequest.Kind kind = request.kind();
        Money price = request.price();
        List<String> notes = new ArrayList<>();

        BigDecimal depositShare = switch (kind) {
            case CAR, HOUSE -> STANDARD_DEPOSIT;
            case OTHER -> BigDecimal.ONE;
        };
        Money recommendedDeposit = price.times(depositShare);
        Money deposit = request.deposit() == null
                ? recommendedDeposit
                : Money.min(price, Money.max(Money.ZERO, request.deposit()));
        int termMonths = request.termMonths() != null ? request.termMonths() : defaultTerm(kind);
        Money borrowed = price.minus(deposit);
        boolean financed = borrowed.isPositive() && termMonths > 0;
        if (borrowed.isPositive() && termMonths <= 0) {
            deposit = price;
            borrowed = Money.ZERO;
        }

        BigDecimal rate = request.annualRate() != null ? request.annualRate() : defaultRate(kind);
        if (kind == PurchaseImpactRequest.Kind.HOUSE
                && request.annualRate() == null
                && deposit.ratioOf(price).compareTo(STANDARD_DEPOSIT) < 0) {
            rate = rate.add(LOW_EQUITY_MARGIN);
            notes.add(
                    "With less than a 20% deposit, banks usually add a low-equity margin. We've added 0.5% to the rate.");
        }
        Money fees = kind == PurchaseImpactRequest.Kind.CAR ? CAR_LOAN_FEES : Money.ZERO;
        LoanQuote loan = financed ? loans.quote(borrowed, rate, termMonths, fees) : null;

        List<PurchaseImpact.CostLine> upfront = request.upfrontCosts() != null
                ? request.upfrontCosts().isPositive()
                        ? List.of(new PurchaseImpact.CostLine("One-off costs", request.upfrontCosts(), null))
                        : List.of()
                : typicalUpfront(kind);
        Money upfrontTotal =
                Money.sum(upfront.stream().map(PurchaseImpact.CostLine::amount).toList());

        List<PurchaseImpact.CostLine> monthly = new ArrayList<>();
        if (loan != null) {
            monthly.add(new PurchaseImpact.CostLine(
                    kind == PurchaseImpactRequest.Kind.HOUSE ? "Mortgage repayment" : "Loan repayment",
                    loan.monthlyRepayment(),
                    kind == PurchaseImpactRequest.Kind.HOUSE ? "mortgage" : "debt-repayments"));
        }
        if (request.ownershipCosts() != null) {
            if (request.ownershipCosts().isPositive()) {
                monthly.add(new PurchaseImpact.CostLine(
                        "Running costs",
                        request.ownershipCosts(),
                        kind == PurchaseImpactRequest.Kind.CAR ? "vehicle" : "rates"));
            }
        } else {
            monthly.addAll(typicalRunning(kind, price));
        }
        if (kind == PurchaseImpactRequest.Kind.HOUSE
                && request.replacedHousingCost() != null
                && request.replacedHousingCost().isPositive()) {
            monthly.add(new PurchaseImpact.CostLine(
                    "Rent you'd stop paying", request.replacedHousingCost().negate(), "rent"));
        }
        Money monthlyTotal =
                Money.sum(monthly.stream().map(PurchaseImpact.CostLine::amount).toList());

        Money income = request.monthlyIncome();
        Money surplusBefore = request.monthlySurplus();
        Money surplusAfter = surplusBefore.minus(monthlyTotal);
        BigDecimal rateBefore = share(surplusBefore, income);
        BigDecimal rateAfter = share(surplusAfter, income);
        BigDecimal costToIncome = share(monthlyTotal, income);

        Money kiwiSaver = Money.ZERO;
        if (kind == PurchaseImpactRequest.Kind.HOUSE && request.firstHome() && request.kiwiSaverBalance() != null) {
            kiwiSaver = Money.max(Money.ZERO, request.kiwiSaverBalance().minus(KIWISAVER_MINIMUM));
            if (kiwiSaver.isPositive()) {
                notes.add("As a first-home buyer you can usually withdraw your KiwiSaver savings, leaving $1,000 in"
                        + " the account. We've counted " + kiwiSaver.formatWhole() + " towards your deposit.");
            }
        }
        Money cashNeeded = deposit.plus(upfrontTotal);
        Money cashAvailable = Money.max(Money.ZERO, request.availableSavings()).plus(kiwiSaver);
        Money shortfall = Money.max(Money.ZERO, cashNeeded.minus(cashAvailable));
        Integer monthsToSave = !shortfall.isPositive()
                ? Integer.valueOf(0)
                : surplusBefore.isPositive()
                        ? Integer.valueOf(
                                (int) ((shortfall.cents() + surplusBefore.cents() - 1) / surplusBefore.cents()))
                        : null;
        LocalDate readyBy = monthsToSave == null ? null : request.today().plusMonths(monthsToSave);

        List<PurchaseImpact.LoanOption> options = new ArrayList<>();
        if (borrowed.isPositive()) {
            for (int term : optionTerms(kind)) {
                LoanQuote option = loans.quote(borrowed, rate, term, fees);
                Money change = monthlyTotal
                        .minus(loan == null ? Money.ZERO : loan.monthlyRepayment())
                        .plus(option.monthlyRepayment());
                options.add(new PurchaseImpact.LoanOption(
                        term, option.monthlyRepayment(), option.totalInterest(), surplusBefore.minus(change)));
            }
        }

        PurchaseImpact.Verdict verdict = verdict(kind, income, surplusAfter, costToIncome, rateAfter);
        String depositGuidance = depositGuidance(kind, price, deposit, request.firstHome());
        String headline = headline(verdict, monthlyTotal, surplusAfter, cashNeeded, shortfall, readyBy);
        notes.addAll(assumptions(kind, request, rate, termMonths));

        return new PurchaseImpact(
                kind,
                price,
                deposit,
                recommendedDeposit,
                depositGuidance,
                loan,
                upfront,
                upfrontTotal,
                monthly,
                monthlyTotal,
                surplusBefore,
                surplusAfter,
                rateBefore,
                rateAfter,
                costToIncome,
                cashNeeded,
                cashAvailable,
                kiwiSaver,
                shortfall,
                monthsToSave,
                readyBy,
                options,
                verdict,
                headline,
                notes);
    }

    static int defaultTerm(PurchaseImpactRequest.Kind kind) {
        return switch (kind) {
            case CAR -> 60;
            case HOUSE -> 360;
            case OTHER -> 0;
        };
    }

    static BigDecimal defaultRate(PurchaseImpactRequest.Kind kind) {
        return switch (kind) {
            case CAR -> CAR_RATE;
            case HOUSE -> HOUSE_RATE;
            case OTHER -> OTHER_RATE;
        };
    }

    private static List<Integer> optionTerms(PurchaseImpactRequest.Kind kind) {
        return switch (kind) {
            case CAR -> List.of(36, 48, 60, 84);
            case HOUSE -> List.of(240, 300, 360);
            case OTHER -> List.of(12, 24, 36);
        };
    }

    private static List<PurchaseImpact.CostLine> typicalUpfront(PurchaseImpactRequest.Kind kind) {
        return switch (kind) {
            case CAR ->
                List.of(
                        new PurchaseImpact.CostLine("Pre-purchase inspection", Money.ofDollars(180), "vehicle"),
                        new PurchaseImpact.CostLine("Change of ownership", Money.ofDollars(10), "vehicle"));
            case HOUSE ->
                List.of(
                        new PurchaseImpact.CostLine("Lawyer and conveyancing", Money.ofDollars(2_000), null),
                        new PurchaseImpact.CostLine("Building inspection", Money.ofDollars(650), null),
                        new PurchaseImpact.CostLine("LIM report", Money.ofDollars(400), null),
                        new PurchaseImpact.CostLine("Moving", Money.ofDollars(1_500), null));
            case OTHER -> List.of();
        };
    }

    private static List<PurchaseImpact.CostLine> typicalRunning(PurchaseImpactRequest.Kind kind, Money price) {
        return switch (kind) {
            case CAR ->
                List.of(
                        new PurchaseImpact.CostLine("Car insurance", Money.ofDollars(85), "insurance"),
                        new PurchaseImpact.CostLine("Registration and WOF", Money.ofDollars(16), "vehicle"),
                        new PurchaseImpact.CostLine("Servicing and repairs", Money.ofDollars(70), "vehicle"));
            case HOUSE ->
                List.of(
                        new PurchaseImpact.CostLine("Council rates", Money.ofDollars(280), "rates"),
                        new PurchaseImpact.CostLine("House insurance", Money.ofDollars(190), "insurance"),
                        new PurchaseImpact.CostLine(
                                "Maintenance",
                                price.dividedBy(BigDecimal.valueOf(1_200), RoundingMode.HALF_UP),
                                "rates"));
            case OTHER -> List.of();
        };
    }

    private static PurchaseImpact.Verdict verdict(
            PurchaseImpactRequest.Kind kind,
            Money income,
            Money surplusAfter,
            BigDecimal costToIncome,
            BigDecimal rateAfter) {
        if (!income.isPositive()) {
            return PurchaseImpact.Verdict.NOT_AFFORDABLE;
        }
        BigDecimal limit = kind == PurchaseImpactRequest.Kind.HOUSE ? HOUSE_COST_LIMIT : CAR_COST_LIMIT;
        if (rateAfter.compareTo(COMFORTABLE_SURPLUS) >= 0
                && (kind == PurchaseImpactRequest.Kind.OTHER || costToIncome.compareTo(limit) <= 0)) {
            return PurchaseImpact.Verdict.COMFORTABLE;
        }
        if (!surplusAfter.isNegative()) {
            return PurchaseImpact.Verdict.TIGHT;
        }
        if (rateAfter.compareTo(STRETCH_SHORTFALL) >= 0) {
            return PurchaseImpact.Verdict.STRETCH;
        }
        return PurchaseImpact.Verdict.NOT_AFFORDABLE;
    }

    private static String headline(
            PurchaseImpact.Verdict verdict,
            Money monthlyTotal,
            Money surplusAfter,
            Money cashNeeded,
            Money shortfall,
            LocalDate readyBy) {
        String monthly = switch (verdict) {
            case COMFORTABLE ->
                "You could afford the monthly costs comfortably, with " + surplusAfter.formatWhole()
                        + " a month still left over.";
            case TIGHT ->
                "You could cover the monthly costs, but only " + surplusAfter.formatWhole()
                        + " a month would be left over.";
            case STRETCH ->
                "The monthly costs of " + monthlyTotal.formatWhole() + " would leave you "
                        + surplusAfter.negate().formatWhole() + " a month short unless you cut back elsewhere.";
            case NOT_AFFORDABLE ->
                "The monthly costs of " + monthlyTotal.formatWhole() + " are more than your budget can take right now.";
        };
        String cash = !shortfall.isPositive()
                ? " You already have the " + cashNeeded.formatWhole() + " you'd need up front."
                : readyBy != null
                        ? " You'd have the " + cashNeeded.formatWhole() + " deposit and costs by "
                                + readyBy.format(MONTH_YEAR) + "."
                        : " You'd need " + shortfall.formatWhole() + " more up front.";
        return monthly + cash;
    }

    private static String depositGuidance(
            PurchaseImpactRequest.Kind kind, Money price, Money deposit, boolean firstHome) {
        String share = deposit.ratioOf(price).movePointRight(2).setScale(0, RoundingMode.HALF_UP) + "%";
        return switch (kind) {
            case HOUSE ->
                firstHome
                        ? "A " + share + " deposit. Most banks want 20%, though first-home buyers can borrow with as"
                                + " little as 5% through Kāinga Ora's First Home Loan."
                        : "A " + share + " deposit. Most banks want 20% to avoid extra fees and a higher rate.";
            case CAR ->
                "A " + share + " deposit. Putting down 20% or more keeps the loan smaller and"
                        + " protects you if the car is worth less than you owe.";
            case OTHER -> deposit.equals(price) ? "Paid in full, with no loan." : "A " + share + " deposit.";
        };
    }

    private static List<String> assumptions(
            PurchaseImpactRequest.Kind kind, PurchaseImpactRequest request, BigDecimal rate, int termMonths) {
        List<String> notes = new ArrayList<>();
        if (request.annualRate() == null && termMonths > 0) {
            notes.add("We've used a typical rate of "
                    + rate.movePointRight(2).stripTrailingZeros().toPlainString()
                    + "%. Check it against the quotes you get.");
        }
        if (request.ownershipCosts() == null && kind != PurchaseImpactRequest.Kind.OTHER) {
            notes.add(
                    kind == PurchaseImpactRequest.Kind.CAR
                            ? "Running costs are typical for a family car and don't include fuel, which you already pay."
                            : "Running costs use typical rates, insurance and 1% of the price a year for maintenance.");
        }
        return notes;
    }

    private static BigDecimal share(Money part, Money whole) {
        return whole.isPositive() ? part.ratioOf(whole).setScale(4, RoundingMode.HALF_UP) : BigDecimal.ZERO;
    }
}
