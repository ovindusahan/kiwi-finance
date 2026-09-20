package nz.kiwifinance.account;

import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import nz.kiwifinance.common.error.ApiException;
import nz.kiwifinance.common.error.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AccountService {

    static final String CASH_WALLET = "Cash";

    private final AccountRepository accounts;
    private final Clock clock;

    @Transactional(readOnly = true)
    public List<Account> list(UUID userId, boolean includeArchived) {
        return includeArchived
                ? accounts.findByUserIdOrderByCreatedAtAsc(userId)
                : accounts.findByUserIdAndArchivedAtIsNullOrderByCreatedAtAsc(userId);
    }

    @Transactional(readOnly = true)
    public Map<UUID, Account> byId(UUID userId) {
        return accounts.findByUserIdOrderByCreatedAtAsc(userId).stream()
                .collect(Collectors.toMap(Account::getId, Function.identity()));
    }

    @Transactional(readOnly = true)
    public Account get(UUID userId, UUID accountId) {
        return accounts.findByIdAndUserId(accountId, userId).orElseThrow(() -> ApiException.notFound("Account"));
    }

    /**
     * Returns the account if it can receive new transactions.
     */
    @Transactional(readOnly = true)
    public Account getOpen(UUID userId, UUID accountId) {
        Account account = get(userId, accountId);
        if (account.isArchived()) {
            throw new ApiException(ErrorCode.ACCOUNT_ARCHIVED, "This account is archived. Restore it first.");
        }
        return account;
    }

    @Transactional
    public Account create(UUID userId, AccountRequests.Create request) {
        Account account = new Account(
                userId,
                request.name().trim(),
                request.type(),
                blankToNull(request.institution()),
                request.currentBalanceCents() == null ? 0 : request.currentBalanceCents());
        if (request.liquid() != null) {
            account.setLiquid(request.liquid());
        }
        return accounts.save(account);
    }

    @Transactional
    Account update(UUID userId, UUID accountId, AccountRequests.Update request) {
        Account account = get(userId, accountId);
        boolean bankFeed = account.getManagedBy() == ManagedBy.BANK_FEED;
        if (bankFeed
                && request.currentBalanceCents() != null
                && request.currentBalanceCents() != account.getCurrentBalanceCents()) {
            throw new ApiException(
                    ErrorCode.ACCOUNT_MANAGED_BY_BANK_FEED,
                    "This balance comes from your bank and updates automatically.");
        }
        if (request.name() != null) {
            account.rename(request.name().trim());
        }
        if (request.type() != null) {
            account.changeType(request.type());
        }
        if (request.institution() != null) {
            account.changeInstitution(blankToNull(request.institution()));
        }
        if (request.currentBalanceCents() != null && !bankFeed) {
            account.setBalance(request.currentBalanceCents());
        }
        if (request.liquid() != null) {
            account.setLiquid(request.liquid());
        }
        if (request.archived() != null) {
            if (request.archived()) {
                account.archive(clock.instant());
                account.setIncludeInEmergencyFund(false);
            } else {
                account.restore();
            }
        }
        return account;
    }

    @Transactional
    void archive(UUID userId, UUID accountId) {
        Account account = get(userId, accountId);
        if (!account.isArchived()) {
            account.archive(clock.instant());
            account.setIncludeInEmergencyFund(false);
        }
    }

    /**
     * Makes one account the emergency fund, or clears the choice when {@code accountId} is
     * {@code null}. A person has at most one emergency fund account.
     */
    @Transactional
    public Optional<Account> setEmergencyFundAccount(UUID userId, UUID accountId) {
        Account chosen = accountId == null ? null : getOpen(userId, accountId);
        accounts.findByUserIdOrderByCreatedAtAsc(userId).stream()
                .filter(Account::isIncludedInEmergencyFund)
                .filter(account -> !account.equals(chosen))
                .forEach(account -> account.setIncludeInEmergencyFund(false));
        accounts.flush();
        if (chosen != null) {
            chosen.setIncludeInEmergencyFund(true);
        }
        return Optional.ofNullable(chosen);
    }

    /**
     * The account that tracks cash in the person's wallet, created the first time it's needed.
     */
    @Transactional
    public Account cashWallet(UUID userId) {
        return accounts.findByUserIdAndArchivedAtIsNullOrderByCreatedAtAsc(userId).stream()
                .filter(account -> account.getType() == AccountType.CASH && CASH_WALLET.equals(account.getName()))
                .findFirst()
                .orElseGet(() -> accounts.save(new Account(userId, CASH_WALLET, AccountType.CASH, null, 0)));
    }

    /**
     * Moves an account's balance by {@code deltaCents} when money is recorded moving in or out. A
     * bank-fed balance is corrected by the bank at its next refresh.
     */
    @Transactional
    public void adjustBalance(UUID userId, UUID accountId, long deltaCents) {
        Account account = getOpen(userId, accountId);
        account.setBalance(account.getCurrentBalanceCents() + deltaCents);
    }

    @Transactional
    public Account createForBankFeed(
            UUID userId, String name, AccountType type, String institution, long balanceCents) {
        Account account = new Account(userId, name, type, institution, balanceCents);
        account.manageBy(ManagedBy.BANK_FEED);
        return accounts.save(account);
    }

    /**
     * Hands an existing account over to a bank feed, for people who tracked it by hand before
     * connecting their bank.
     */
    @Transactional
    public Account attachToBankFeed(UUID userId, UUID accountId) {
        Account account = getOpen(userId, accountId);
        if (account.getManagedBy() == ManagedBy.BANK_FEED) {
            throw new ApiException(
                    ErrorCode.BANK_FEED_ACCOUNT_LINK_INVALID, "That account is already linked to a bank feed.");
        }
        account.manageBy(ManagedBy.BANK_FEED);
        return account;
    }

    @Transactional
    public void releaseFromBankFeed(UUID accountId) {
        accounts.findById(accountId).ifPresent(account -> account.manageBy(ManagedBy.USER));
    }

    @Transactional
    public void updateBalanceFromBankFeed(UUID accountId, long balanceCents) {
        accounts.findById(accountId)
                .filter(account -> account.getManagedBy() == ManagedBy.BANK_FEED)
                .ifPresent(account -> account.setBalance(balanceCents));
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
