package nz.kiwifinance.privacy;

import java.time.LocalDate;
import java.time.ZoneId;
import lombok.RequiredArgsConstructor;
import nz.kiwifinance.common.web.CurrentUser;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
class PrivacyController {

    private static final ZoneId NEW_ZEALAND = ZoneId.of("Pacific/Auckland");

    private final DataExportService exports;

    @GetMapping("/api/v1/auth/me/export")
    ResponseEntity<DataExport> export(CurrentUser user) {
        DataExport export = exports.export(user.id());
        String fileName = "kiwi-finance-data-%s.json".formatted(LocalDate.ofInstant(export.exportedAt(), NEW_ZEALAND));
        return ResponseEntity.ok()
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment()
                                .filename(fileName)
                                .build()
                                .toString())
                .body(export);
    }
}
