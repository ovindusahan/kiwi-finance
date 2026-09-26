package nz.kiwifinance.movement;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import nz.kiwifinance.account.Account;
import nz.kiwifinance.account.AccountService;
import nz.kiwifinance.account.ManagedBy;
import nz.kiwifinance.common.error.ApiException;
import nz.kiwifinance.common.error.ErrorCode;
import nz.kiwifinance.common.web.MoneyResponse;
import nz.kiwifinance.engine.time.NzTime;
import nz.kiwifinance.transaction.TransactionService;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Records money moving between accounts, out of them or into them. Balances and transactions
 * change straight away, so every figure, insight and projection reflects the move. Kiwi Finance
 * does not move money at the bank; on bank-fed accounts the bank's own transaction replaces the
 * recorded one when it arrives.
 */
@Service
@RequiredArgsConstructor
@Log4j2
public class MoneyMovementService {

    static final int MAX_RECENT = 50;

    private final MoneyMovementRepository movements;
    private final AccountService accounts;
    private final TransactionService transactions;
    private final Clock clock;

    @Transactional
    MovementResponse record(UUID userId, MovementRequest request) {
        LocalDate today = NzTime.today(clock);
        LocalDate movedOn = request.movedOn() == null ? today : request.movedOn();
        if (movedOn.isAfter(today)) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "The date can't be in the future.");
        }
        Account from = account(userId, request.fromAccountId());
        Account to = account(userId, request.toAccountId());
        switch (request.type()) {
            case TRANSFER -> {
                require(from != null && to != null, "Choose the account the money left and the one it went to.");
                require(!from.getId().equals(to.getId()), "Choose two different accounts.");
            }
            case WITHDRAWAL -> require(from != null && to == null, "Choose the account the money came out of.");
            case DEPOSIT -> require(to != null && from == null, "Choose the account the money went into.");
        }
        long amount = request.amountCents();
        String note = request.note() == null || request.note().isBlank()
                ? null
                : request.note().trim();
        MoneyMovement movement = movements.save(new MoneyMovement(
                userId,
                request.type(),
                from == null ? null : from.getId(),
                to == null ? null : to.getId(),
                amount,
                movedOn,
                note));

        if (from != null) {
            String description = to != null ? "Transfer to " + to.getName() : describe("Withdrawal", note);
            boolean transfer = request.type() == MovementType.TRANSFER;
            transactions.recordMovement(
                    userId,
                    from.getId(),
                    movement.getId(),
                    movedOn,
                    -amount,
                    description,
                    transfer ? null : request.categoryId(),
                    transfer,
                    note,
                    fromBank(from));
            accounts.adjustBalance(userId, from.getId(), -amount);
        }
        if (to != null) {
            String description = from != null ? "Transfer from " + from.getName() : describe("Deposit", note);
            boolean transfer = request.type() == MovementType.TRANSFER || request.categoryId() == null;
            transactions.recordMovement(
                    userId,
                    to.getId(),
                    movement.getId(),
                    movedOn,
                    amount,
                    description,
                    request.type() == MovementType.TRANSFER ? null : request.categoryId(),
                    transfer,
                    note,
                    fromBank(to));
            accounts.adjustBalance(userId, to.getId(), amount);
        }
        log.info("Recorded {} of {} cents for user {}", request.type(), amount, userId);
        return view(movement, accounts.byId(userId));
    }

    @Transactional(readOnly = true)
    List<MovementResponse> recent(UUID userId, UUID accountId, int limit) {
        Map<UUID, Account> byId = accounts.byId(userId);
        return movements.findRecent(userId, accountId, Limit.of(Math.clamp(limit, 1, MAX_RECENT))).stream()
                .map(movement -> view(movement, byId))
                .toList();
    }

    private Account account(UUID userId, UUID accountId) {
        return accountId == null ? null : accounts.getOpen(userId, accountId);
    }

    private static boolean fromBank(Account account) {
        return account.getManagedBy() == ManagedBy.BANK_FEED;
    }

    private static String describe(String kind, String note) {
        return note == null ? kind : kind + ": " + note;
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, message);
        }
    }

    private static MovementResponse view(MoneyMovement movement, Map<UUID, Account> byId) {
        return new MovementResponse(
                movement.getId(),
                movement.getType(),
                movement.getFromAccountId(),
                name(movement.getFromAccountId(), byId),
                movement.getToAccountId(),
                name(movement.getToAccountId(), byId),
                MoneyResponse.of(movement.getAmountCents()),
                movement.getMovedOn(),
                movement.getNote());
    }

    private static String name(UUID accountId, Map<UUID, Account> byId) {
        return Optional.ofNullable(accountId)
                .map(byId::get)
                .map(Account::getName)
                .orElse(null);
    }
}
