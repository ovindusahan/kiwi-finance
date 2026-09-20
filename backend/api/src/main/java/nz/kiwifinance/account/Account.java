package nz.kiwifinance.account;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import nz.kiwifinance.common.persistence.AuditableEntity;

@Entity
@Table(name = "accounts")
@Getter
public class Account extends AuditableEntity {

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "account_type", nullable = false)
    private AccountType type;

    private String institution;

    @Column(name = "current_balance_cents", nullable = false)
    private long currentBalanceCents;

    @Setter(AccessLevel.PACKAGE)
    @Column(name = "is_liquid", nullable = false)
    private boolean liquid;

    @Column(name = "include_in_emergency_fund", nullable = false)
    private boolean includeInEmergencyFund;

    @Enumerated(EnumType.STRING)
    @Column(name = "managed_by", nullable = false)
    private ManagedBy managedBy = ManagedBy.USER;

    @Column(name = "archived_at")
    private Instant archivedAt;

    protected Account() {}

    public Account(UUID userId, String name, AccountType type, String institution, long balanceCents) {
        this.userId = userId;
        this.name = name;
        this.type = type;
        this.institution = institution;
        this.currentBalanceCents = balanceCents;
        this.liquid = type.isLiquidByDefault();
    }

    public boolean isIncludedInEmergencyFund() {
        return includeInEmergencyFund;
    }

    public boolean isArchived() {
        return archivedAt != null;
    }

    void rename(String name) {
        this.name = name;
    }

    void changeType(AccountType type) {
        this.type = type;
    }

    void changeInstitution(String institution) {
        this.institution = institution;
    }

    void setBalance(long cents) {
        this.currentBalanceCents = cents;
    }

    void setIncludeInEmergencyFund(boolean include) {
        this.includeInEmergencyFund = include;
    }

    void manageBy(ManagedBy managedBy) {
        this.managedBy = managedBy;
    }

    void archive(Instant now) {
        this.archivedAt = now;
    }

    void restore() {
        this.archivedAt = null;
    }
}
