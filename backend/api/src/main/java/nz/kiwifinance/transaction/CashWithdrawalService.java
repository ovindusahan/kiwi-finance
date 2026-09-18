package nz.kiwifinance.transaction;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import nz.kiwifinance.account.Account;
import nz.kiwifinance.account.AccountService;
import nz.kiwifinance.account.AccountType;
import nz.kiwifinance.category.CategoryService;
import nz.kiwifinance.common.error.ApiException;
import nz.kiwifinance.common.error.ErrorCode;
import nz.kiwifinance.common.web.MoneyResponse;
import nz.kiwifinance.engine.time.NzTime;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Cash taken out at an ATM or over the counter leaves no trace of what it bought. We ask the person
 * what it went on, record that spending against a cash wallet account, and treat the withdrawal
 * itself as a transfer so nothing is counted twice.
 */
@Service
@RequiredArgsConstructor
public class CashWithdrawalService {

    /** How far back to ask about cash withdrawals. Older ones are left alone. */
    static final int LOOKBACK_DAYS = 60;

    private static final Pattern CASH =
            Pattern.compile("\\b(ATM|CASH\\s*(WITHDRAWAL|WDL|OUT)|WITHDRAWAL)\\b", Pattern.CASE_INSENSITIVE);

    private final TransactionRepository transactions;
    private final TransactionService transactionService;
    private final AccountService accounts;
    private final CategoryService categories;
    private final Clock clock;

    public static boolean looksLikeCash(String description, String merchant) {
        return CASH.matcher(description == null ? "" : description).find()
                || CASH.matcher(merchant == null ? "" : merchant).find();
    }

    @Transactional(readOnly = true)
    public List<CashWithdrawals.View> unsorted(UUID userId) {
        Map<UUID, Account> byId = accounts.byId(userId);
        return transactions.findUnsortedOutgoings(userId, NzTime.today(clock).minusDays(LOOKBACK_DAYS)).stream()
                .filter(transaction -> looksLikeCash(transaction.getDescription(), transaction.getMerchant()))
                .filter(transaction -> Optional.ofNullable(byId.get(transaction.getAccountId()))
                        .map(account -> account.getType() != AccountType.CASH)
                        .orElse(true))
                .map(transaction -> new CashWithdrawals.View(
                        transaction.getId(),
                        transaction.getAccountId(),
                        Optional.ofNullable(byId.get(transaction.getAccountId()))
                                .map(Account::getName)
                                .orElse(null),
                        transaction.getPostedOn(),
                        MoneyResponse.of(-transaction.getAmountCents()),
                        transaction.getDescription()))
                .toList();
    }

    @Transactional
    CashWithdrawals.Result sort(UUID userId, UUID transactionId, CashWithdrawals.Spending request) {
        Transaction withdrawal = transactions
                .findActive(transactionId, userId)
                .filter(transaction -> transaction.getAmountCents() < 0)
                .orElseThrow(() -> ApiException.notFound("Cash withdrawal"));
        if (withdrawal.isTransfer()) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "That withdrawal has already been sorted.");
        }
        long amount = -withdrawal.getAmountCents();
        long spent = request.lines().stream()
                .mapToLong(CashWithdrawals.Line::amountCents)
                .sum();
        if (spent > amount) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "That adds up to more than the cash you took out.");
        }
        request.lines().forEach(line -> categories.get(userId, line.categoryId()));

        Account wallet = accounts.cashWallet(userId);
        String from = Optional.ofNullable(accounts.byId(userId).get(withdrawal.getAccountId()))
                .map(Account::getName)
                .orElse("your account");
        withdrawal.setTransfer(true);
        if (withdrawal.getNotes() == null) {
            withdrawal.setNotes("Cash, moved to your cash wallet");
        }
        LocalDate day = withdrawal.getPostedOn();
        transactionService.recordMovement(
                userId, wallet.getId(), null, day, amount, "Cash from " + from, null, true, null, false);
        for (CashWithdrawals.Line line : request.lines()) {
            String note = line.note() == null || line.note().isBlank()
                    ? null
                    : line.note().trim();
            transactionService.recordMovement(
                    userId,
                    wallet.getId(),
                    null,
                    day,
                    -line.amountCents(),
                    note == null ? "Cash spending" : note,
                    line.categoryId(),
                    false,
                    null,
                    false);
        }
        accounts.adjustBalance(userId, wallet.getId(), amount - spent);
        return new CashWithdrawals.Result(
                wallet.getId(),
                MoneyResponse.of(wallet.getCurrentBalanceCents()),
                request.lines().size());
    }
}
