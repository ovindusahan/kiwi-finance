package nz.kiwifinance.category;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.Getter;
import nz.kiwifinance.common.persistence.AuditableEntity;

/**
 * Assigns a category to transactions whose merchant or description matches a pattern, such as
 * "COUNTDOWN" to Groceries.
 */
@Entity
@Table(name = "categorisation_rules")
@Getter
public class CategorisationRule extends AuditableEntity {

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "match_type", nullable = false)
    private MatchType matchType;

    @Column(nullable = false)
    private String pattern;

    @Column(name = "category_id", nullable = false)
    private UUID categoryId;

    @Column(nullable = false)
    private int priority;

    protected CategorisationRule() {}

    CategorisationRule(UUID userId, MatchType matchType, String pattern, UUID categoryId, int priority) {
        this.userId = userId;
        this.matchType = matchType;
        this.pattern = pattern;
        this.categoryId = categoryId;
        this.priority = priority;
    }

    boolean matches(String merchant, String description) {
        return (merchant != null && matchType.matches(merchant, pattern)) || matchType.matches(description, pattern);
    }

    void update(MatchType matchType, String pattern, UUID categoryId, Integer priority) {
        if (matchType != null) {
            this.matchType = matchType;
        }
        if (pattern != null) {
            this.pattern = pattern;
        }
        if (categoryId != null) {
            this.categoryId = categoryId;
        }
        if (priority != null) {
            this.priority = priority;
        }
    }
}
