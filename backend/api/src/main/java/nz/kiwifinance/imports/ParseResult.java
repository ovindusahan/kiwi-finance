package nz.kiwifinance.imports;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

record ParseResult(CsvFormat format, List<ParsedRow> rows, List<SkippedRow> skipped) {

    @Schema(name = "ImportSkippedRow")
    record SkippedRow(int lineNumber, String reason) {}
}
