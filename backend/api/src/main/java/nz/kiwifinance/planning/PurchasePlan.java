package nz.kiwifinance.planning;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import nz.kiwifinance.common.persistence.AuditableEntity;

/**
 * A saved purchase the person is working towards. Its affordability is reassessed every time it
 * is viewed, so it tracks their changing position.
 */
@Entity
@Table(name = "purchase_plans")
class PurchasePlan extends AuditableEntity {

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Getter(AccessLevel.PACKAGE)
    @Column(name = "item_name", nullable = false)
    private String itemName;

    @Getter(AccessLevel.PACKAGE)
    @Column(name = "price_cents", nullable = false)
    private long priceCents;

    @Getter(AccessLevel.PACKAGE)
    @Column(name = "desired_date")
    private LocalDate desiredDate;

    @Getter(AccessLevel.PACKAGE)
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Funding funding;

    @Getter(AccessLevel.PACKAGE)
    @Column(name = "deposit_cents")
    private Long depositCents;

    @Getter(AccessLevel.PACKAGE)
    @Column(name = "loan_rate", precision = 6, scale = 5)
    private BigDecimal loanRate;

    @Getter(AccessLevel.PACKAGE)
    @Column(name = "loan_term_months")
    private Integer loanTermMonths;

    @Getter(AccessLevel.PACKAGE)
    @Column(name = "loan_fees_cents")
    private Long loanFeesCents;

    @Getter(AccessLevel.PACKAGE)
    @Column(name = "first_home", nullable = false)
    private boolean firstHome;

    @Getter(AccessLevel.PACKAGE)
    @Column(name = "goal_id")
    private UUID goalId;

    protected PurchasePlan() {}

    PurchasePlan(UUID userId) {
        this.userId = userId;
    }

    void apply(PlanningRequests.Purchase request) {
        itemName = request.itemName().trim();
        priceCents = request.priceCents();
        desiredDate = request.desiredDate();
        funding = request.funding();
        boolean financed = request.funding() == Funding.FINANCE;
        depositCents = financed ? request.depositCents() : null;
        loanRate = financed ? request.loanRate() : null;
        loanTermMonths = financed ? request.loanTermMonths() : null;
        loanFeesCents = financed ? request.loanFeesCents() : null;
        firstHome = request.firstHome();
    }

    PlanningRequests.Purchase toRequest() {
        return new PlanningRequests.Purchase(
                itemName,
                priceCents,
                desiredDate,
                funding,
                depositCents,
                loanRate,
                loanTermMonths,
                loanFeesCents,
                firstHome);
    }

    void linkGoal(UUID goalId) {
        this.goalId = goalId;
    }
}
