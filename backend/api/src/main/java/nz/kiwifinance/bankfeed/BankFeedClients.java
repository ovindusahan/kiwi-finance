package nz.kiwifinance.bankfeed;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class BankFeedClients {

    private final Map<BankFeedProvider, BankFeedClient> byProvider = new EnumMap<>(BankFeedProvider.class);

    BankFeedClients(List<BankFeedClient> clients) {
        clients.forEach(client -> byProvider.put(client.provider(), client));
    }

    public BankFeedClient forProvider(BankFeedProvider provider) {
        BankFeedClient client = byProvider.get(provider);
        if (client == null) {
            throw new IllegalStateException("No client for " + provider);
        }
        return client;
    }
}
