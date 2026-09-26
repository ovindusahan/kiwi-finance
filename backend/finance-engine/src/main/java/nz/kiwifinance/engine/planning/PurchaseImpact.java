package nz.kiwifinance.engine.planning;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import nz.kiwifinance.engine.money.Money;

/**
 * What a big purchase would cost, how it would be paid for, and what it would do to the person's
 * monthly budget.
 *
 * @param recommendedDeposit the deposit we suggest for this kind of purchase
 * @param loan the loan for the rest of the price, or {@code null} when paying cash
 * @param monthlyCosts the new monthly costs, with any rent the purchase replaces as a negative line
 * @param cashNeeded the deposit plus one-off costs
 * @param kiwiSaverAvailable the KiwiSaver that could go towards a first home's deposit
 * @param monthsToSave months of usual saving before the cash needed is in hand, or {@code null}
 *     when there is nothing spare to save
 * @param options the same purchase over other loan terms
 */
public record PurchaseImpact(
        PurchaseImpactRequest.Kind kind,
        Money price,
        Money deposit,
        Money recommendedDeposit,
        String depositGuidance,
        LoanQuote loan,
        List<CostLine> upfrontCosts,
        Money upfrontTotal,
        List<CostLine> monthlyCosts,
        Money monthlyTotal,
        Money surplusBefore,
        Money surplusAfter,
        BigDecimal savingsRateBefore,
        BigDecimal savingsRateAfter,
        BigDecimal costToIncome,
        Money cashNeeded,
        Money cashAvailable,
        Money kiwiSaverAvailable,
        Money cashShortfall,
        Integer monthsToSave,
        LocalDate readyBy,
        List<LoanOption> options,
        Verdict verdict,
        String headline,
        List<String> notes) {

    public PurchaseImpact {
        upfrontCosts = List.copyOf(upfrontCosts);
        monthlyCosts = List.copyOf(monthlyCosts);
        options = List.copyOf(options);
        notes = List.copyOf(notes);
    }

    public enum Verdict {
        COMFORTABLE,
        TIGHT,
        STRETCH,
        NOT_AFFORDABLE
    }

    /**
     * @param budgetCategory the budget category this cost belongs in, as a category slug
     */
    public record CostLine(String label, Money amount, String budgetCategory) {}

    public record LoanOption(int termMonths, Money monthlyRepayment, Money totalInterest, Money surplusAfter) {}
}
