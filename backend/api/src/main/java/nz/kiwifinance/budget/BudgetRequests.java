package nz.kiwifinance.budget;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

final class BudgetRequests {

    private BudgetRequests() {}

    @Schema(name = "BudgetRequest")
    record Save(
            @Size(max = 100) String name,
            @NotEmpty @Size(max = 100) List<@Valid Line> lines) {}

    /**
     * @param categoryId the category, or {@code null} for an allowance covering uncategorised spending
     */
    @Schema(name = "BudgetLineRequest")
    record Line(
            UUID categoryId,
            @NotNull @PositiveOrZero Long limitCents,
            @Size(max = 300) String rationale) {}
}
