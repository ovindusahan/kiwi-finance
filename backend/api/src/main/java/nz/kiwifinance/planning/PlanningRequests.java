package nz.kiwifinance.planning;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import nz.kiwifinance.engine.planning.PurchaseImpactRequest;

final class PlanningRequests {

    private PlanningRequests() {}

    /**
     * A purchase to check. When {@code funding} is {@code FINANCE}, the deposit is what needs saving
     * and the rest is borrowed at {@code loanRate} over {@code loanTermMonths}.
     *
     * @param firstHome whether this is a first home, which may allow a KiwiSaver withdrawal
     */
    @Schema(name = "PurchaseRequest")
    record Purchase(
            @NotBlank @Size(max = 100) String itemName,
            @NotNull @Positive Long priceCents,
            LocalDate desiredDate,
            @NotNull Funding funding,
            @PositiveOrZero Long depositCents,
            @DecimalMin("0") @DecimalMax("0.5") BigDecimal loanRate,
            @Min(1) @Max(360) Integer loanTermMonths,
            @PositiveOrZero Long loanFeesCents,
            boolean firstHome) {

        @Schema(hidden = true)
        @AssertTrue(message = "needs a deposit, interest rate and term when financing")
        public boolean isFinancingComplete() {
            return funding != Funding.FINANCE || (depositCents != null && loanRate != null && loanTermMonths != null);
        }

        @Schema(hidden = true)
        @AssertTrue(message = "must be less than the price")
        public boolean isDepositValid() {
            return depositCents == null || priceCents == null || depositCents < priceCents;
        }
    }

    @Schema(name = "LoanRequest")
    record Loan(
            @NotNull @Positive Long amountCents,
            @NotNull @DecimalMin("0") @DecimalMax("0.5") BigDecimal annualRate,
            @NotNull @Min(1) @Max(360) Integer termMonths,
            @PositiveOrZero Long feesCents) {}

    /**
     * A big purchase to weigh up. Leave the optional amounts out to use typical New Zealand figures.
     *
     * @param termMonths the loan term, or zero to pay cash
     */
    @Schema(name = "PurchaseImpactRequest")
    record Impact(
            @NotNull PurchaseImpactRequest.Kind kind,
            @NotNull @Positive Long priceCents,
            @PositiveOrZero Long depositCents,
            @DecimalMin("0") @DecimalMax("0.5") BigDecimal annualRate,
            @Min(0) @Max(360) Integer termMonths,
            @PositiveOrZero Long upfrontCostsCents,
            @PositiveOrZero Long ownershipCostsCents,
            boolean firstHome) {}
}
