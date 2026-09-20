package nz.kiwifinance.user;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import nz.kiwifinance.common.web.MoneyResponse;
import nz.kiwifinance.engine.nzrules.TaxCode;
import nz.kiwifinance.engine.time.PayFrequency;
import org.jspecify.annotations.Nullable;

@Schema(name = "Profile")
public record ProfileResponse(
        @Nullable LocalDate dateOfBirth,
        @Nullable Region region,
        int householdSize,
        int dependants,
        EmploymentType employmentType,
        @Nullable HousingType housingType,
        boolean singleIncomeHousehold,
        TaxCode taxCode,
        boolean hasStudentLoan,
        boolean kiwiSaverMember,
        @Nullable BigDecimal kiwiSaverRate,
        @Nullable LocalDate kiwiSaverJoinedOn,
        @Nullable MoneyResponse kiwiSaverBalance,
        boolean firstHomeBuyer,
        PayFrequency payFrequency,
        BigDecimal savingsInterestRate,
        BigDecimal targetSavingsRate,
        boolean onboarded,
        @Nullable Instant onboardedAt) {

    static ProfileResponse from(UserProfile profile) {
        return new ProfileResponse(
                profile.getDateOfBirth(),
                profile.getRegion(),
                profile.getHouseholdSize(),
                profile.getDependants(),
                profile.getEmploymentType(),
                profile.getHousingType(),
                profile.isSingleIncomeHousehold(),
                profile.getTaxCode(),
                profile.hasStudentLoan(),
                profile.isKiwiSaverMember(),
                profile.getKiwiSaverRate(),
                profile.getKiwiSaverJoinedOn(),
                profile.getKiwiSaverBalanceCents() == null
                        ? null
                        : MoneyResponse.of(profile.getKiwiSaverBalanceCents()),
                profile.isFirstHomeBuyer(),
                profile.getPayFrequency(),
                profile.getSavingsInterestRate(),
                profile.getTargetSavingsRate(),
                profile.getOnboardedAt() != null,
                profile.getOnboardedAt());
    }
}
