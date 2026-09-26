package nz.kiwifinance.dashboard;

import io.swagger.v3.oas.annotations.media.Schema;
import nz.kiwifinance.category.CategoryResponse;
import nz.kiwifinance.common.web.MoneyResponse;
import nz.kiwifinance.engine.insights.Insight;
import org.jspecify.annotations.Nullable;

@Schema(name = "Insight")
public record InsightResponse(
        String key,
        Insight.Tone tone,
        String title,
        String message,
        @Nullable MoneyResponse amount,
        @Nullable CategoryResponse category,
        Insight.@Nullable Action action) {}
