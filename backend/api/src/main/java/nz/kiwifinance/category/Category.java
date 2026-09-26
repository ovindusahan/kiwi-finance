package nz.kiwifinance.category;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.Getter;
import nz.kiwifinance.common.persistence.AuditableEntity;
import nz.kiwifinance.engine.analysis.CategoryGroup;
import nz.kiwifinance.engine.analysis.CategoryRef;

/**
 * A spending or income category. System categories are shared by everyone and have a slug;
 * custom categories belong to one person.
 */
@Entity
@Table(name = "categories")
@Getter
public class Category extends AuditableEntity {

    @Column(name = "user_id", updatable = false)
    private UUID userId;

    @Column(updatable = false)
    private String slug;

    @Column(nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "category_group", nullable = false)
    private CategoryGroup group;

    @Column(nullable = false)
    private String icon;

    @Column(nullable = false)
    private String colour;

    protected Category() {}

    Category(UUID userId, String name, CategoryGroup group, String icon, String colour) {
        this.userId = userId;
        this.name = name;
        this.group = group;
        this.icon = icon;
        this.colour = colour;
    }

    public boolean isSystem() {
        return userId == null;
    }

    public CategoryRef toRef() {
        return new CategoryRef(getId().toString(), name, group);
    }

    void update(String name, CategoryGroup group, String icon, String colour) {
        if (name != null) {
            this.name = name;
        }
        if (group != null) {
            this.group = group;
        }
        if (icon != null) {
            this.icon = icon;
        }
        if (colour != null) {
            this.colour = colour;
        }
    }
}
