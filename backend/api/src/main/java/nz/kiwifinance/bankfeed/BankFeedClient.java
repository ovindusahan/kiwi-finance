package nz.kiwifinance.bankfeed;

import java.time.Instant;
import java.util.List;

/**
 * Reads accounts and transactions from a bank feed provider for one connection. Implementations
 * throw {@link BankFeedException} for provider failures.
 */
public interface BankFeedClient {

    BankFeedProvider provider();

    List<FeedAccount> accounts(BankConnection connection);

    /**
     * Settled transactions on the account posted after {@code from} and up to {@code to}.
     */
    List<FeedTransaction> transactions(BankConnection connection, String externalAccountId, Instant from, Instant to);

    /**
     * Withdraws the provider access held by this connection, if the provider supports it.
     */
    void revoke(BankConnection connection);
}
