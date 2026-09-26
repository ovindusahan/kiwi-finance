package nz.kiwifinance.privacy;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;
import java.util.Map;

@Schema(name = "DataExport")
public record DataExport(
        Instant exportedAt,

        @Schema(description = "Every stored record, by table, with column names as stored")
        Map<String, List<Map<String, Object>>> tables) {}
