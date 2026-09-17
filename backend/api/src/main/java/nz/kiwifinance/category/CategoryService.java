package nz.kiwifinance.category;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import nz.kiwifinance.common.error.ApiException;
import nz.kiwifinance.common.error.ErrorCode;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CategoryService {

    private final CategoryRepository categories;
    private final ApplicationEventPublisher events;

    @Transactional(readOnly = true)
    public List<Category> list(UUID userId) {
        return categories.findVisibleTo(userId);
    }

    @Transactional(readOnly = true)
    public Map<UUID, Category> byId(UUID userId) {
        return categories.findVisibleTo(userId).stream()
                .collect(Collectors.toMap(Category::getId, Function.identity()));
    }

    @Transactional(readOnly = true)
    public Category get(UUID userId, UUID categoryId) {
        return categories.findVisible(categoryId, userId).orElseThrow(() -> ApiException.notFound("Category"));
    }

    @Transactional(readOnly = true)
    public Optional<Category> system(String slug) {
        return categories.findBySlugAndUserIdIsNull(slug);
    }

    @Transactional
    Category create(UUID userId, CategoryRequests.Create request) {
        String name = request.name().trim();
        ensureNameAvailable(userId, name);
        return categories.save(new Category(userId, name, request.group(), request.icon(), request.colour()));
    }

    @Transactional
    Category update(UUID userId, UUID categoryId, CategoryRequests.Update request) {
        Category category = editable(userId, categoryId);
        String name = request.name() == null ? null : request.name().trim();
        if (name != null && !name.equalsIgnoreCase(category.getName())) {
            ensureNameAvailable(userId, name);
        }
        category.update(name, request.group(), request.icon(), request.colour());
        return category;
    }

    @Transactional
    void delete(UUID userId, UUID categoryId) {
        Category category = editable(userId, categoryId);
        events.publishEvent(new CategoryDeleted(userId, categoryId));
        categories.delete(category);
    }

    private Category editable(UUID userId, UUID categoryId) {
        Category category = get(userId, categoryId);
        if (category.isSystem()) {
            throw new ApiException(ErrorCode.CATEGORY_READ_ONLY, "Built-in categories can't be changed.");
        }
        return category;
    }

    private void ensureNameAvailable(UUID userId, String name) {
        boolean clashesWithSystem = categories.findVisibleTo(userId).stream()
                .anyMatch(c -> c.isSystem() && c.getName().equalsIgnoreCase(name));
        if (clashesWithSystem || categories.existsCustomName(userId, name)) {
            throw new ApiException(ErrorCode.CATEGORY_NAME_TAKEN, "You already have a category called " + name + ".");
        }
    }
}
