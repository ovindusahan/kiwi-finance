package nz.kiwifinance.common.web;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Base64;
import java.util.UUID;
import nz.kiwifinance.common.error.ApiException;
import nz.kiwifinance.common.error.ErrorCode;

/**
 * Opaque keyset-pagination cursors for lists ordered by date then ID, newest first.
 */
public final class Cursors {

    private Cursors() {}

    public record DateCursor(LocalDate date, UUID id) {}

    public static String encode(LocalDate date, UUID id) {
        String raw = date + "|" + id;
        return Base64.getUrlEncoder().withoutPadding().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    public static DateCursor decode(String cursor) {
        try {
            String raw = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8);
            String[] parts = raw.split("\\|", 2);
            if (parts.length != 2) {
                throw invalid();
            }
            return new DateCursor(LocalDate.parse(parts[0]), UUID.fromString(parts[1]));
        } catch (IllegalArgumentException | DateTimeParseException e) {
            throw invalid();
        }
    }

    private static ApiException invalid() {
        return new ApiException(ErrorCode.INVALID_CURSOR, "The cursor is not valid. Start again from the first page.");
    }
}
