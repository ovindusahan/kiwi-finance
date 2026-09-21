package nz.kiwifinance.bankfeed;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import nz.kiwifinance.account.AccountService;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Records the latest account details reported by a provider and copies balances onto the linked
 * accounts.
 */
@Component
@RequiredArgsConstructor
class FeedAccountRefresher {

    private final BankFeedAccountRepository feedAccounts;
    private final AccountService accounts;

    @Transactional
    void refresh(BankConnection connection, List<FeedAccount> accountsSeen) {
        Map<String, BankFeedAccount> existing =
                feedAccounts.findByConnectionIdOrderByNameAsc(connection.getId()).stream()
                        .collect(Collectors.toMap(BankFeedAccount::getExternalId, Function.identity()));
        for (FeedAccount seen : accountsSeen) {
            BankFeedAccount account = existing.computeIfAbsent(
                    seen.externalId(), id -> new BankFeedAccount(connection.getId(), connection.getUserId(), id));
            account.refresh(seen);
            feedAccounts.save(account);
            if (account.isSyncEnabled() && account.getAccountId() != null && seen.balanceCents() != null) {
                accounts.updateBalanceFromBankFeed(account.getAccountId(), seen.balanceCents());
            }
        }
    }
}
