package nz.kiwifinance.goal;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.UUID;

final class GoalRequests {

    private GoalRequests() {}

    /**
     * @param priority 1 is most important; money already saved for higher-priority goals is kept
     *     out of affordability checks
     * @param linkedAccountId a savings account that holds this goal's money; its balance becomes
     *     the amount saved
     * @param startingAmountCents money already saved when the goal is created
     */
    @Schema(name = "GoalRequest")
    record Save(
            @NotBlank @Size(max = 100) String name,
            @NotNull GoalType type,
            @NotNull @Positive Long targetCents,
            LocalDate targetDate,
            @Min(1) @Max(3) Integer priority,
            @PositiveOrZero Long monthlyContributionCents,
            UUID linkedAccountId,
            @PositiveOrZero Long startingAmountCents) {}

    @Schema(name = "GoalContributionRequest")
    record Contribute(
            @NotNull Long amountCents,
            LocalDate contributedOn,
            @Size(max = 200) String note) {}

    @Schema(name = "GoalStatusRequest")
    record ChangeStatus(@NotNull GoalStatus status) {}
}
