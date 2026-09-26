package nz.kiwifinance.transaction;

import java.time.LocalDate;
import java.util.UUID;

/**
 * @param direction {@code IN} for money in, {@code OUT} for money out, or {@code null} for both
 */
record TransactionFilter(
        UUID accountId,
        UUID categoryId,
        boolean uncategorised,
        LocalDate from,
        LocalDate to,
        String search,
        Direction direction) {

    enum Direction {
        IN,
        OUT
    }
}
