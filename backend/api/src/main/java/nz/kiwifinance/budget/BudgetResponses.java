package nz.kiwifinance.budget;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;
import nz.kiwifinance.category.CategoryResponse;
import nz.kiwifinance.common.web.MoneyResponse;
import nz.kiwifinance.engine.budgeting.BudgetProgress;
import nz.kiwifinance.engine.budgeting.BudgetRecommendation;
import nz.kiwifinance.engine.explain.Explanation;
import org.jspecify.annotations.Nullable;

public final class BudgetResponses {

    private BudgetResponses() {}

    @Schema(name = "BudgetRecommendation")
    public record Recommendation(
            MoneyResponse monthlyIncome,
            List<RecommendedLine> lines,
            MoneyResponse plannedSpending,
            MoneyResponse plannedSavings,
            BigDecimal savingsRate,
            MoneyResponse targetSavings,
            boolean meetsTarget,
            Explanation explanation) {}

    @Schema(name = "RecommendedBudgetLine")
    public record RecommendedLine(
            @Nullable CategoryResponse category,
            BudgetRecommendation.Kind kind,
            MoneyResponse typical,
            MoneyResponse recommended,
            boolean trimmed,
            String rationale) {}

    @Schema(name = "Budget")
    public record Current(UUID id, String name, MoneyResponse totalLimit, Progress progress) {}

    @Schema(name = "BudgetProgress")
    public record Progress(
            YearMonth month,
            int dayOfMonth,
            int daysInMonth,
            MoneyResponse totalLimit,
            MoneyResponse totalSpent,
            MoneyResponse totalRemaining,
            int linesOver,
            int linesAtRisk,
            List<Line> lines) {}

    @Schema(name = "BudgetLine")
    public record Line(
            @Nullable CategoryResponse category,
            MoneyResponse limit,
            MoneyResponse spent,
            MoneyResponse remaining,
            BigDecimal usedFraction,
            BudgetProgress.Status status,
            @Nullable String rationale) {}
}
