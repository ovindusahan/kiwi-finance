package nz.kiwifinance.imports;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Schema(name = "ImportBatch")
record ImportResponse(
        UUID id,
        UUID accountId,
        CsvFormat format,
        String fileName,
        int rowCount,
        int importedCount,
        int duplicateCount,
        List<ParseResult.SkippedRow> skippedRows,
        Instant createdAt) {

    static ImportResponse from(ImportBatch batch, List<ParseResult.SkippedRow> skipped) {
        return new ImportResponse(
                batch.getId(),
                batch.getAccountId(),
                batch.getFormat(),
                batch.getFileName(),
                batch.getRowCount(),
                batch.getImportedCount(),
                batch.getDuplicateCount(),
                skipped,
                batch.getCreatedAt());
    }
}
