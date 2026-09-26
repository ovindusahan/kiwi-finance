package nz.kiwifinance.emergencyfund;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import nz.kiwifinance.common.web.MoneyResponse;
import nz.kiwifinance.engine.emergencyfund.EmergencyFundPlan;
import nz.kiwifinance.engine.explain.Explanation;
import org.jspecify.annotations.Nullable;

/**
 * @param suggestedPerPayPeriod the suggested saving expressed in the person's pay frequency
 * @param account the account that holds the fund, or {@code null} until the person chooses one
 * @param suggestedAccount the account we would suggest when none is chosen yet
 * @param remind whether to remind the person to choose an account now
 */
@Schema(name = "EmergencyFund")
public record EmergencyFundResponse(
        MoneyResponse monthlyEssentials,
        int targetMonths,
        MoneyResponse target,
        MoneyResponse current,
        MoneyResponse shortfall,
        BigDecimal progress,
        BigDecimal monthsCovered,
        EmergencyFundPlan.Status status,
        MoneyResponse suggestedMonthlyContribution,
        MoneyResponse suggestedPerPayPeriod,
        String payPeriod,
        @Nullable Integer monthsToTarget,
        List<Milestone> milestones,
        List<String> reasons,
        @Nullable FundAccount account,
        @Nullable FundAccount suggestedAccount,
        boolean remind,
        Explanation explanation) {

    @Schema(name = "EmergencyFundMilestone")
    public record Milestone(String label, MoneyResponse amount, boolean reached) {}

    @Schema(name = "EmergencyFundAccount")
    public record FundAccount(
            UUID id, String name, @Nullable String institution, MoneyResponse balance, boolean fromBank) {}
}
