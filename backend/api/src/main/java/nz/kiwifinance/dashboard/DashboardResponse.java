package nz.kiwifinance.dashboard;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import nz.kiwifinance.analysis.AnalysisResponses;
import nz.kiwifinance.category.CategoryResponse;
import nz.kiwifinance.common.web.MoneyResponse;
import nz.kiwifinance.engine.emergencyfund.EmergencyFundPlan;
import nz.kiwifinance.engine.progress.Streaks;
import nz.kiwifinance.engine.score.KiwiScore;
import nz.kiwifinance.goal.GoalResponses;
import org.jspecify.annotations.Nullable;

/**
 * Everything the home screen shows, in one request.
 *
 * @param setup the first steps a new person works through, in order
 * @param emergencyFundReminder whether to remind the person to choose an emergency fund account
 * @param thisMonthByCategory spending so far this month by category, largest first
 */
@Schema(name = "Dashboard")
public record DashboardResponse(
        String displayName,
        LocalDate today,
        AnalysisResponses.Month thisMonth,
        AnalysisResponses.Typical typical,
        MoneyResponse netWorth,
        MoneyResponse availableToSpend,
        Score score,
        Streaks streaks,
        EmergencyFund emergencyFund,
        @Nullable Budget budget,
        List<GoalResponses.View> goals,
        List<AnalysisResponses.Recurring> upcomingBills,
        List<InsightResponse> insights,
        List<SetupStep> setup,
        int achievementsUnlocked,
        int achievementsTotal,
        boolean emergencyFundReminder,
        List<CategoryMonth> thisMonthByCategory) {

    @Schema(name = "DashboardScore")
    public record Score(int score, KiwiScore.Band band, String bandLabel, String nextStep) {}

    @Schema(name = "DashboardEmergencyFund")
    public record EmergencyFund(
            MoneyResponse target,
            MoneyResponse current,
            BigDecimal progress,
            BigDecimal monthsCovered,
            int targetMonths,
            EmergencyFundPlan.Status status) {}

    @Schema(name = "DashboardBudget")
    public record Budget(
            MoneyResponse totalLimit,
            MoneyResponse totalSpent,
            MoneyResponse totalRemaining,
            int linesOver,
            int linesAtRisk,
            int dayOfMonth,
            int daysInMonth) {}

    /**
     * @param category the category, or {@code null} for uncategorised spending
     * @param typical what the person usually spends on it in a whole month
     * @param limit the budget limit for it, if the budget has one
     */
    @Schema(name = "DashboardCategoryMonth")
    public record CategoryMonth(
            @Nullable CategoryResponse category,
            MoneyResponse spent,
            MoneyResponse typical,
            @Nullable MoneyResponse limit) {}

    @Schema(name = "SetupStep")
    public record SetupStep(String key, String title, String description, boolean done, String href) {}
}
