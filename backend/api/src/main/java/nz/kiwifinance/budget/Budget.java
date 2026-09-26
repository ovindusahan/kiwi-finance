package nz.kiwifinance.budget;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import nz.kiwifinance.common.persistence.AuditableEntity;

/**
 * A monthly spending plan. Each person has at most one active budget.
 */
@Entity
@Table(name = "budgets")
class Budget extends AuditableEntity {

    @Getter(AccessLevel.PACKAGE)
    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Getter(AccessLevel.PACKAGE)
    @Column(nullable = false)
    private String name;

    @Getter(AccessLevel.PACKAGE)
    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    protected Budget() {}

    Budget(UUID userId, String name) {
        this.userId = userId;
        this.name = name;
    }

    void rename(String name) {
        this.name = name;
    }

    void deactivate() {
        active = false;
    }
}
