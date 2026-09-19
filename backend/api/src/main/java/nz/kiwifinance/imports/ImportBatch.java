package nz.kiwifinance.imports;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import nz.kiwifinance.common.persistence.AuditableEntity;

@Entity
@Table(name = "import_batches")
class ImportBatch extends AuditableEntity {

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Getter(AccessLevel.PACKAGE)
    @Column(name = "account_id", nullable = false, updatable = false)
    private UUID accountId;

    @Getter(AccessLevel.PACKAGE)
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false)
    private CsvFormat format;

    @Getter(AccessLevel.PACKAGE)
    @Column(name = "file_name", nullable = false, updatable = false)
    private String fileName;

    @Getter(AccessLevel.PACKAGE)
    @Column(name = "row_count", nullable = false)
    private int rowCount;

    @Getter(AccessLevel.PACKAGE)
    @Column(name = "imported_count", nullable = false)
    private int importedCount;

    @Getter(AccessLevel.PACKAGE)
    @Column(name = "duplicate_count", nullable = false)
    private int duplicateCount;

    protected ImportBatch() {}

    ImportBatch(
            UUID userId,
            UUID accountId,
            CsvFormat format,
            String fileName,
            int rowCount,
            int importedCount,
            int duplicateCount) {
        this.userId = userId;
        this.accountId = accountId;
        this.format = format;
        this.fileName = fileName;
        this.rowCount = rowCount;
        this.importedCount = importedCount;
        this.duplicateCount = duplicateCount;
    }
}
