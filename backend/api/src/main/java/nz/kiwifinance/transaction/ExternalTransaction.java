package nz.kiwifinance.transaction;

import java.time.LocalDate;
import java.util.UUID;

/**
 * A transaction arriving from outside, such as a bank feed or a CSV file.
 *
 * @param externalId identifies the transaction within its account, so re-imports update rather
 *     than duplicate
 * @param suggestedCategoryId the category the source suggests, used only if no rule matches
 */
public record ExternalTransaction(
        String externalId,
        LocalDate postedOn,
        long amountCents,
        String description,
        String merchant,
        UUID suggestedCategoryId,
        boolean transfer) {}
