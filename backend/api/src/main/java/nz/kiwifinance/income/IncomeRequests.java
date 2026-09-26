package nz.kiwifinance.income;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import nz.kiwifinance.engine.nzrules.TaxCode;
import nz.kiwifinance.engine.time.PayFrequency;

final class IncomeRequests {

    private IncomeRequests() {}

    /**
     * @param taxCode the tax code for this income; defaults to the profile's tax code
     */
    @Schema(name = "IncomeSourceRequest")
    record Save(
            @NotBlank @Size(max = 100) String name,
            @NotNull IncomeType type,
            @NotNull @Positive Long amountCents,
            @NotNull AmountBasis basis,
            @NotNull PayFrequency frequency,
            TaxCode taxCode,
            LocalDate startsOn,
            LocalDate endsOn) {

        @Schema(hidden = true)
        @AssertTrue(message = "must not be before the start date")
        public boolean isEndsOnValid() {
            return startsOn == null || endsOn == null || !endsOn.isBefore(startsOn);
        }
    }

    /**
     * @param kiwiSaverRate the employee rate, or {@code null} if not a KiwiSaver member
     * @param payDate the date to use for tax rules; defaults to today
     */
    @Schema(name = "PayCalculationRequest")
    record PayCalculation(
            @NotNull @Positive Long amountCents,
            @NotNull AmountBasis basis,
            @NotNull PayFrequency frequency,
            @NotNull TaxCode taxCode,
            boolean studentLoan,
            @DecimalMin("0.03") @DecimalMax("0.10") BigDecimal kiwiSaverRate,
            LocalDate payDate) {}
}
