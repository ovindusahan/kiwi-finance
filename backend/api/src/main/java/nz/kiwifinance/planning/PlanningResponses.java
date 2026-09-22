package nz.kiwifinance.planning;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;
import nz.kiwifinance.category.CategoryResponse;
import nz.kiwifinance.common.web.MoneyResponse;
import nz.kiwifinance.engine.explain.Explanation;
import nz.kiwifinance.engine.planning.AffordabilityResult;
import nz.kiwifinance.engine.planning.LoanQuote;
import nz.kiwifinance.engine.planning.PurchaseImpact;
import nz.kiwifinance.engine.planning.PurchaseImpactRequest;
import org.jspecify.annotations.Nullable;

public final class PlanningResponses {

    private PlanningResponses() {}

    @Schema(name = "Affordability")
    public record Affordability(
            AffordabilityResult.Verdict verdict,
            String headline,
            MoneyResponse price,
            MoneyResponse target,
            MoneyResponse availableNow,
            MoneyResponse shortfall,
            MoneyResponse monthlySavingCapacity,
            @Nullable Integer monthsNeeded,
            @Nullable LocalDate realisticDate,
            @Nullable LocalDate desiredDate,
            @Nullable MoneyResponse requiredMonthlySaving,
            MoneyResponse requiredPerPayPeriod,
            String payPeriod,
            @Nullable Loan loan,
            List<Lever> levers,
            List<String> warnings,
            Explanation explanation) {

        static Affordability from(AffordabilityResult result, List<Explanation.Assumption> context) {
            return new Affordability(
                    result.verdict(),
                    result.headline(),
                    MoneyResponse.of(result.price()),
                    MoneyResponse.of(result.target()),
                    MoneyResponse.of(result.availableNow()),
                    MoneyResponse.of(result.shortfall()),
                    MoneyResponse.of(result.monthlySavingCapacity()),
                    result.monthsNeeded(),
                    result.realisticDate(),
                    result.desiredDate(),
                    MoneyResponse.of(result.requiredMonthlySaving()),
                    MoneyResponse.of(result.requiredPerPayPeriod()),
                    result.payFrequency().periodName(),
                    result.loan() == null ? null : Loan.from(result.loan()),
                    result.levers().stream().map(Lever::from).toList(),
                    result.warnings(),
                    new Explanation(
                            result.explanation().summary(),
                            result.explanation().steps(),
                            Stream.concat(result.explanation().assumptions().stream(), context.stream())
                                    .toList()));
        }
    }

    @Schema(name = "AffordabilityLever")
    public record Lever(
            AffordabilityResult.Lever.Kind kind,
            String title,
            String description,
            @Nullable MoneyResponse monthlyAmount,
            @Nullable MoneyResponse perPayPeriod,
            @Nullable LocalDate resultingDate,
            @Nullable Integer monthsSooner) {

        static Lever from(AffordabilityResult.Lever lever) {
            return new Lever(
                    lever.kind(),
                    lever.title(),
                    lever.description(),
                    MoneyResponse.of(lever.monthlyAmount()),
                    MoneyResponse.of(lever.perPayPeriod()),
                    lever.resultingDate(),
                    lever.monthsSooner());
        }
    }

    @Schema(name = "LoanQuote")
    public record Loan(
            MoneyResponse principal,
            BigDecimal annualRate,
            int termMonths,
            MoneyResponse fees,
            MoneyResponse monthlyRepayment,
            MoneyResponse weeklyRepayment,
            MoneyResponse fortnightlyRepayment,
            MoneyResponse totalRepaid,
            MoneyResponse totalInterest,
            Explanation explanation) {

        static Loan from(LoanQuote quote) {
            return new Loan(
                    MoneyResponse.of(quote.principal()),
                    quote.annualRate(),
                    quote.termMonths(),
                    MoneyResponse.of(quote.fees()),
                    MoneyResponse.of(quote.monthlyRepayment()),
                    MoneyResponse.of(quote.weeklyRepayment()),
                    MoneyResponse.of(quote.fortnightlyRepayment()),
                    MoneyResponse.of(quote.totalRepaid()),
                    MoneyResponse.of(quote.totalInterest()),
                    quote.explanation());
        }
    }

    @Schema(name = "PurchasePlan")
    public record PurchasePlan(
            UUID id,
            String itemName,
            MoneyResponse price,
            @Nullable LocalDate desiredDate,
            Funding funding,
            @Nullable MoneyResponse deposit,
            @Nullable BigDecimal loanRate,
            @Nullable Integer loanTermMonths,
            @Nullable MoneyResponse loanFees,
            boolean firstHome,
            @Nullable UUID goalId,
            Affordability assessment,
            Instant createdAt) {}

    /**
     * @param goalsAffected goals that would fall behind if the purchase went ahead
     */
    @Schema(name = "PurchaseImpact")
    public record Impact(
            PurchaseImpactRequest.Kind kind,
            MoneyResponse price,
            MoneyResponse deposit,
            MoneyResponse recommendedDeposit,
            String depositGuidance,
            @Nullable Loan loan,
            List<Cost> upfrontCosts,
            MoneyResponse upfrontTotal,
            List<Cost> monthlyCosts,
            MoneyResponse monthlyTotal,
            MoneyResponse income,
            MoneyResponse surplusBefore,
            MoneyResponse surplusAfter,
            BigDecimal savingsRateBefore,
            BigDecimal savingsRateAfter,
            BigDecimal costToIncome,
            MoneyResponse cashNeeded,
            MoneyResponse cashAvailable,
            MoneyResponse kiwiSaverAvailable,
            MoneyResponse cashShortfall,
            @Nullable Integer monthsToSave,
            @Nullable LocalDate readyBy,
            List<Option> options,
            PurchaseImpact.Verdict verdict,
            String headline,
            List<String> notes,
            List<AffectedGoal> goalsAffected) {}

    /**
     * @param category the budget category this cost belongs in, if any
     */
    @Schema(name = "PurchaseCost")
    public record Cost(
            String label, MoneyResponse amount, @Nullable CategoryResponse category) {}

    @Schema(name = "PurchaseLoanOption")
    public record Option(
            int termMonths, MoneyResponse monthlyRepayment, MoneyResponse totalInterest, MoneyResponse surplusAfter) {}

    @Schema(name = "PurchaseAffectedGoal")
    public record AffectedGoal(UUID goalId, String name, String change) {}
}
