package nz.kiwifinance.category;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import nz.kiwifinance.engine.analysis.CategoryGroup;

final class CategoryRequests {

    private static final String COLOUR = "^#[0-9A-F]{6}$";

    private CategoryRequests() {}

    @Schema(name = "CreateCategoryRequest")
    record Create(
            @NotBlank @Size(max = 60) String name,
            @NotNull CategoryGroup group,
            @NotBlank @Size(max = 40) String icon,

            @NotBlank @Pattern(regexp = COLOUR, message = "must be a hex colour such as #22C55E")
            String colour) {}

    @Schema(name = "UpdateCategoryRequest")
    record Update(
            @Size(min = 1, max = 60) String name,
            CategoryGroup group,
            @Size(min = 1, max = 40) String icon,

            @Pattern(regexp = COLOUR, message = "must be a hex colour such as #22C55E")
            String colour) {}
}
