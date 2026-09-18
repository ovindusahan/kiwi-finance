package nz.kiwifinance.transaction;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import nz.kiwifinance.common.persistence.AuditableEntity;

@Entity
@Table(name = "transactions")
@Getter
public class Transaction extends AuditableEntity {

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "account_id", nullable = false, updatable = false)
    private UUID accountId;

    @Column(name = "posted_on", nullable = false)
    private LocalDate postedOn;

    @Column(name = "amount_cents", nullable = false)
    private long amountCents;

    @Column(nullable = false)
    private String description;

    private String merchant;

    @Column(name = "category_id")
    private UUID categoryId;

    @Enumerated(EnumType.STRING)
    @Column(name = "category_source")
    private CategorySource categorySource;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false)
    private TransactionSource source;

    @Column(name = "external_id", updatable = false)
    private String externalId;

    @Setter(AccessLevel.PACKAGE)
    @Column(name = "is_transfer", nullable = false)
    private boolean transfer;

    private String notes;

    /** The recorded transfer, withdrawal or deposit this transaction belongs to, if any. */
    @Column(name = "movement_id", updatable = false)
    private UUID movementId;

    /** Set on a recorded movement in a bank-fed account until the bank's own copy arrives. */
    @Column(name = "awaiting_bank_copy", nullable = false)
    private boolean awaitingBankCopy;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    protected Transaction() {}

    Transaction(
            UUID userId,
            UUID accountId,
            TransactionSource source,
            String externalId,
            LocalDate postedOn,
            long amountCents,
            String description,
            String merchant) {
        this.userId = userId;
        this.accountId = accountId;
        this.source = source;
        this.externalId = externalId;
        this.postedOn = postedOn;
        this.amountCents = amountCents;
        this.description = description;
        this.merchant = merchant;
    }

    boolean isEditableDetails() {
        return source == TransactionSource.MANUAL;
    }

    boolean isCategoryChosenByUser() {
        return categorySource == CategorySource.USER;
    }

    void categorise(UUID categoryId, CategorySource source) {
        this.categoryId = categoryId;
        this.categorySource = categoryId == null ? null : source;
    }

    void editDetails(LocalDate postedOn, Long amountCents, String description, String merchant) {
        if (postedOn != null) {
            this.postedOn = postedOn;
        }
        if (amountCents != null) {
            this.amountCents = amountCents;
        }
        if (description != null) {
            this.description = description;
        }
        if (merchant != null) {
            this.merchant = merchant.isBlank() ? null : merchant;
        }
    }

    void setNotes(String notes) {
        this.notes = notes == null || notes.isBlank() ? null : notes;
    }

    /**
     * Applies the latest copy of an imported transaction. Returns whether anything changed.
     */
    boolean refreshFrom(ExternalTransaction incoming) {
        boolean changed = !Objects.equals(postedOn, incoming.postedOn())
                || amountCents != incoming.amountCents()
                || !Objects.equals(description, incoming.description())
                || !Objects.equals(merchant, incoming.merchant());
        postedOn = incoming.postedOn();
        amountCents = incoming.amountCents();
        description = incoming.description();
        merchant = incoming.merchant();
        return changed;
    }

    void softDelete(Instant now) {
        this.deletedAt = now;
    }

    void linkToMovement(UUID movementId, boolean awaitingBankCopy) {
        this.movementId = movementId;
        this.awaitingBankCopy = awaitingBankCopy;
    }

    /**
     * Retires a recorded movement once the bank's copy of it has arrived, passing on what the person
     * told us about it.
     */
    void replaceWith(Transaction bankCopy, Instant now) {
        bankCopy.setTransfer(transfer);
        if (categoryId != null && !bankCopy.isCategoryChosenByUser()) {
            bankCopy.categorise(categoryId, CategorySource.USER);
        }
        if (bankCopy.getNotes() == null && notes != null) {
            bankCopy.setNotes(notes);
        }
        this.awaitingBankCopy = false;
        softDelete(now);
    }
}
