package nz.kiwifinance.category;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;
import nz.kiwifinance.engine.analysis.CategoryGroup;
import org.jspecify.annotations.Nullable;

@Schema(name = "Category")
public record CategoryResponse(
        UUID id, @Nullable String slug, String name, CategoryGroup group, String icon, String colour, boolean custom) {

    public static CategoryResponse from(Category category) {
        if (category == null) {
            return null;
        }
        return new CategoryResponse(
                category.getId(),
                category.getSlug(),
                category.getName(),
                category.getGroup(),
                category.getIcon(),
                category.getColour(),
                !category.isSystem());
    }
}
