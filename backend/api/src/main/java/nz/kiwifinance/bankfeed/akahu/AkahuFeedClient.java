package nz.kiwifinance.bankfeed.akahu;

import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import nz.kiwifinance.bankfeed.BankConnection;
import nz.kiwifinance.bankfeed.BankFeedClient;
import nz.kiwifinance.bankfeed.BankFeedProvider;
import nz.kiwifinance.bankfeed.ConnectionMethod;
import nz.kiwifinance.bankfeed.CredentialStore;
import nz.kiwifinance.bankfeed.FeedAccount;
import nz.kiwifinance.bankfeed.FeedTransaction;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
class AkahuFeedClient implements BankFeedClient {

    private final AkahuApi api;
    private final CredentialStore credentialStore;
    private final AkahuProperties properties;

    @Override
    public BankFeedProvider provider() {
        return BankFeedProvider.AKAHU;
    }

    @Override
    public List<FeedAccount> accounts(BankConnection connection) {
        return api.accounts(credentials(connection)).stream()
                .map(AkahuMapper::toFeedAccount)
                .toList();
    }

    @Override
    public List<FeedTransaction> transactions(
            BankConnection connection, String externalAccountId, Instant from, Instant to) {
        return api.transactions(credentials(connection), externalAccountId, from, to).stream()
                .filter(transaction ->
                        transaction.id() != null && transaction.amount() != null && transaction.date() != null)
                .map(AkahuMapper::toFeedTransaction)
                .toList();
    }

    /**
     * Personal-app tokens belong to the person, who revokes them in Akahu. OAuth tokens are
     * revoked here so access ends as soon as they disconnect.
     */
    @Override
    public void revoke(BankConnection connection) {
        if (connection.getMethod() == ConnectionMethod.OAUTH) {
            api.revoke(credentials(connection));
        }
    }

    AkahuCredentials credentials(BankConnection connection) {
        return credentialStore.read(connection, AkahuCredentials.class).withAppToken(properties.appToken());
    }
}
