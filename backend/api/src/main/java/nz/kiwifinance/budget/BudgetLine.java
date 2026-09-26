package nz.kiwifinance.budget;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import nz.kiwifinance.common.persistence.AuditableEntity;

/**
 * A monthly limit for one category. A line without a category covers uncategorised spending.
 */
@Entity
@Table(name = "budget_lines")
class BudgetLine extends AuditableEntity {

    @Column(name = "budget_id", nullable = false, updatable = false)
    private UUID budgetId;

    @Getter(AccessLevel.PACKAGE)
    @Column(name = "category_id", updatable = false)
    private UUID categoryId;

    @Getter(AccessLevel.PACKAGE)
    @Column(name = "limit_cents", nullable = false)
    private long limitCents;

    @Getter(AccessLevel.PACKAGE)
    private String rationale;

    protected BudgetLine() {}

    BudgetLine(UUID budgetId, UUID categoryId, long limitCents, String rationale) {
        this.budgetId = budgetId;
        this.categoryId = categoryId;
        this.limitCents = limitCents;
        this.rationale = rationale;
    }
}
