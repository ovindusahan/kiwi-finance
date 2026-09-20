package nz.kiwifinance.bankfeed;

import java.time.LocalDate;

/**
 * A settled transaction reported by a bank feed provider, in provider-neutral form.
 *
 * @param suggestedCategorySlug the system category the provider's enrichment maps to, if any
 */
public record FeedTransaction(
        String externalId,
        LocalDate postedOn,
        long amountCents,
        String description,
        String merchant,
        String suggestedCategorySlug,
        boolean transfer) {}
