package nz.kiwifinance.category;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

final class RuleRequests {

    private RuleRequests() {}

    @Schema(name = "CreateRuleRequest")
    record Create(
            @NotNull MatchType matchType,
            @NotBlank @Size(min = 2, max = 100) String pattern,
            @NotNull UUID categoryId,
            @Min(0) @Max(100) int priority) {}

    @Schema(name = "UpdateRuleRequest")
    record Update(
            MatchType matchType,
            @Size(min = 2, max = 100) String pattern,
            UUID categoryId,
            @Min(0) @Max(100) Integer priority) {}
}
