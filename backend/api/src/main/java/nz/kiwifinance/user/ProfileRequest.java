package nz.kiwifinance.user;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.PositiveOrZero;
import java.math.BigDecimal;
import java.time.LocalDate;
import nz.kiwifinance.engine.nzrules.TaxCode;
import nz.kiwifinance.engine.time.PayFrequency;

@Schema(name = "ProfileRequest")
public record ProfileRequest(
        @Past LocalDate dateOfBirth,
        Region region,
        @Min(1) @Max(20) int householdSize,
        @Min(0) @Max(20) int dependants,
        @NotNull EmploymentType employmentType,
        HousingType housingType,
        boolean singleIncomeHousehold,
        @NotNull TaxCode taxCode,
        boolean hasStudentLoan,
        boolean kiwiSaverMember,
        @DecimalMin("0.03") @DecimalMax("0.10") BigDecimal kiwiSaverRate,
        @Past LocalDate kiwiSaverJoinedOn,
        @PositiveOrZero Long kiwiSaverBalanceCents,
        boolean firstHomeBuyer,
        @NotNull PayFrequency payFrequency,
        @NotNull @DecimalMin("0") @DecimalMax("0.2") BigDecimal savingsInterestRate,
        @NotNull @DecimalMin("0") @DecimalMax("0.8") BigDecimal targetSavingsRate) {

    @Schema(hidden = true)
    @AssertTrue(message = "is required for KiwiSaver members")
    public boolean isKiwiSaverRateProvided() {
        return !kiwiSaverMember || kiwiSaverRate != null;
    }

    @Schema(hidden = true)
    @AssertTrue(message = "must not be annual")
    public boolean isPayFrequencyAllowed() {
        return payFrequency != PayFrequency.ANNUALLY;
    }
}
