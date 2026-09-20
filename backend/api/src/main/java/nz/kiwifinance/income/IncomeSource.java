package nz.kiwifinance.income;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.util.UUID;
import lombok.Getter;
import nz.kiwifinance.common.persistence.AuditableEntity;
import nz.kiwifinance.engine.nzrules.TaxCode;
import nz.kiwifinance.engine.time.PayFrequency;

@Entity
@Table(name = "income_sources")
@Getter
public class IncomeSource extends AuditableEntity {

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "income_type", nullable = false)
    private IncomeType type;

    @Column(name = "amount_cents", nullable = false)
    private long amountCents;

    @Enumerated(EnumType.STRING)
    @Column(name = "amount_basis", nullable = false)
    private AmountBasis basis;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PayFrequency frequency;

    @Enumerated(EnumType.STRING)
    @Column(name = "tax_code")
    private TaxCode taxCode;

    @Column(name = "starts_on")
    private LocalDate startsOn;

    @Column(name = "ends_on")
    private LocalDate endsOn;

    protected IncomeSource() {}

    IncomeSource(UUID userId) {
        this.userId = userId;
    }

    void apply(IncomeRequests.Save request) {
        name = request.name().trim();
        type = request.type();
        amountCents = request.amountCents();
        basis = request.basis();
        frequency = request.frequency();
        taxCode = request.taxCode();
        startsOn = request.startsOn();
        endsOn = request.endsOn();
    }

    public boolean isCurrent(LocalDate today) {
        return (startsOn == null || !startsOn.isAfter(today)) && (endsOn == null || !endsOn.isBefore(today));
    }
}
