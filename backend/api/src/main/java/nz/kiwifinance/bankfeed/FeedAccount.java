package nz.kiwifinance.bankfeed;

import java.time.Instant;
import nz.kiwifinance.account.AccountType;

/**
 * An account reported by a bank feed provider, in provider-neutral form.
 *
 * @param balanceCents the current balance, negative when money is owed, or {@code null} if unknown
 */
public record FeedAccount(
        String externalId,
        String name,
        String institution,
        AccountType type,
        String maskedNumber,
        Long balanceCents,
        Instant balanceUpdatedAt,
        boolean supportsTransactions,
        boolean active) {}
