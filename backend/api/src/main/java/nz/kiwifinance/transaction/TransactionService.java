package nz.kiwifinance.transaction;

import java.time.Clock;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import nz.kiwifinance.account.Account;
import nz.kiwifinance.account.AccountService;
import nz.kiwifinance.category.CategorisationRuleService;
import nz.kiwifinance.category.Category;
import nz.kiwifinance.category.CategoryDeleted;
import nz.kiwifinance.category.CategoryMatcher;
import nz.kiwifinance.category.CategoryResponse;
import nz.kiwifinance.category.CategoryService;
import nz.kiwifinance.common.error.ApiException;
import nz.kiwifinance.common.error.ErrorCode;
import nz.kiwifinance.common.web.Cursors;
import nz.kiwifinance.common.web.MoneyResponse;
import nz.kiwifinance.common.web.PageResponse;
import nz.kiwifinance.engine.analysis.TransactionRecord;
import nz.kiwifinance.engine.money.Money;
import org.springframework.context.event.EventListener;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class TransactionService {

    static final int MAX_PAGE_SIZE = 200;

    /** How many days apart a recorded movement and the bank's copy of it can be. */
    static final int MATCH_WINDOW_DAYS = 7;

    private final TransactionRepository transactions;
    private final AccountService accounts;
    private final CategoryService categories;
    private final CategorisationRuleService rules;
    private final Clock clock;

    @Transactional(readOnly = true)
    PageResponse<TransactionResponse> list(UUID userId, TransactionFilter filter, String cursor, int limit) {
        int size = Math.clamp(limit, 1, MAX_PAGE_SIZE);
        Cursors.DateCursor position = cursor == null || cursor.isBlank() ? null : Cursors.decode(cursor);
        Sort sort = Sort.by(Sort.Order.desc("postedOn"), Sort.Order.desc("id"));
        List<Transaction> rows = transactions.findBy(
                TransactionSpecifications.matching(userId, filter, position),
                query -> query.sortBy(sort).limit(size + 1).all());

        boolean hasMore = rows.size() > size;
        List<Transaction> page = hasMore ? rows.subList(0, size) : rows;
        String nextCursor = hasMore
                ? Cursors.encode(page.getLast().getPostedOn(), page.getLast().getId())
                : null;
        return new PageResponse<>(toResponses(userId, page), nextCursor);
    }

    @Transactional(readOnly = true)
    TransactionResponse get(UUID userId, UUID id) {
        return toResponses(userId, List.of(find(userId, id))).getFirst();
    }

    @Transactional
    TransactionResponse create(UUID userId, TransactionRequests.Create request) {
        accounts.getOpen(userId, request.accountId());
        Transaction transaction = new Transaction(
                userId,
                request.accountId(),
                TransactionSource.MANUAL,
                null,
                request.postedOn(),
                request.amountCents(),
                request.description().trim(),
                blankToNull(request.merchant()));
        transaction.setTransfer(request.transfer());
        transaction.setNotes(request.notes());
        if (request.categoryId() != null) {
            categories.get(userId, request.categoryId());
            transaction.categorise(request.categoryId(), CategorySource.USER);
        } else {
            rules.matcherFor(userId)
                    .match(transaction.getMerchant(), transaction.getDescription())
                    .ifPresent(categoryId -> transaction.categorise(categoryId, CategorySource.RULE));
        }
        return toResponses(userId, List.of(transactions.save(transaction))).getFirst();
    }

    @Transactional
    TransactionResponse update(UUID userId, UUID id, TransactionRequests.Update request) {
        Transaction transaction = find(userId, id);
        if (request.changesDetails() && !transaction.isEditableDetails()) {
            throw new ApiException(
                    ErrorCode.TRANSACTION_MANAGED_BY_BANK_FEED,
                    "Only the category, notes and transfer flag can be changed on imported transactions.");
        }
        transaction.editDetails(
                request.postedOn(),
                request.amountCents(),
                request.description() == null ? null : request.description().trim(),
                request.merchant());
        if (request.transfer() != null) {
            transaction.setTransfer(request.transfer());
        }
        if (request.notes() != null) {
            transaction.setNotes(request.notes());
        }
        return toResponses(userId, List.of(transaction)).getFirst();
    }

    @Transactional
    TransactionResponse setCategory(UUID userId, UUID id, UUID categoryId) {
        Transaction transaction = find(userId, id);
        if (categoryId != null) {
            categories.get(userId, categoryId);
        }
        transaction.categorise(categoryId, CategorySource.USER);
        return toResponses(userId, List.of(transaction)).getFirst();
    }

    /**
     * Sets the same category on several transactions at once. Every transaction must belong to the
     * person, otherwise nothing changes.
     */
    @Transactional
    int categorise(UUID userId, List<UUID> transactionIds, UUID categoryId) {
        if (categoryId != null) {
            categories.get(userId, categoryId);
        }
        List<Transaction> found =
                transactionIds.stream().distinct().map(id -> find(userId, id)).toList();
        found.forEach(transaction -> transaction.categorise(categoryId, CategorySource.USER));
        return found.size();
    }

    @Transactional
    void delete(UUID userId, UUID id) {
        Transaction transaction = find(userId, id);
        if (transaction.getSource() == TransactionSource.BANK_FEED) {
            throw new ApiException(
                    ErrorCode.TRANSACTION_MANAGED_BY_BANK_FEED,
                    "Transactions from your bank can't be deleted. Mark it as a transfer to leave it out of reports.");
        }
        transaction.softDelete(clock.instant());
    }

    /**
     * Re-applies categorisation rules to every transaction whose category was not chosen by the
     * person. Returns how many transactions changed category.
     */
    @Transactional
    public int applyRules(UUID userId) {
        CategoryMatcher matcher = rules.matcherFor(userId);
        int changed = 0;
        for (Transaction transaction : transactions.findAutomaticallyCategorised(userId)) {
            Optional<UUID> match = matcher.match(transaction.getMerchant(), transaction.getDescription());
            if (match.isPresent() && !match.get().equals(transaction.getCategoryId())) {
                transaction.categorise(match.get(), CategorySource.RULE);
                changed++;
            }
        }
        return changed;
    }

    /**
     * Inserts new transactions and refreshes existing ones, matched by external ID within the
     * account. Running the same import twice changes nothing.
     */
    @Transactional
    public ImportResult importTransactions(
            UUID userId, UUID accountId, TransactionSource source, List<ExternalTransaction> incoming) {
        if (incoming.isEmpty()) {
            return ImportResult.EMPTY;
        }
        Map<String, Transaction> existing = transactions
                .findByAccountIdAndExternalIdIn(
                        accountId,
                        incoming.stream().map(ExternalTransaction::externalId).toList())
                .stream()
                .collect(Collectors.toMap(Transaction::getExternalId, Function.identity()));
        CategoryMatcher matcher = rules.matcherFor(userId);

        int created = 0;
        int updated = 0;
        int unchanged = 0;
        List<Transaction> toSave = new ArrayList<>();
        for (ExternalTransaction item : incoming) {
            Transaction transaction = existing.get(item.externalId());
            if (transaction == null) {
                transaction = new Transaction(
                        userId,
                        accountId,
                        source,
                        item.externalId(),
                        item.postedOn(),
                        item.amountCents(),
                        item.description(),
                        item.merchant());
                transaction.setTransfer(item.transfer());
                autoCategorise(transaction, item, matcher);
                existing.put(item.externalId(), transaction);
                toSave.add(transaction);
                created++;
            } else {
                boolean changed = transaction.refreshFrom(item);
                if (!transaction.isCategoryChosenByUser()) {
                    UUID before = transaction.getCategoryId();
                    autoCategorise(transaction, item, matcher);
                    changed |= !Objects.equals(before, transaction.getCategoryId());
                }
                if (changed) {
                    updated++;
                } else {
                    unchanged++;
                }
            }
        }
        transactions.saveAll(toSave);
        if (source == TransactionSource.BANK_FEED) {
            replaceRecordedMovements(accountId, toSave);
        }
        return new ImportResult(created, updated, unchanged);
    }

    /**
     * Records one side of a transfer, withdrawal or deposit the person told us about.
     *
     * @param awaitingBankCopy whether a bank feed will later deliver the same transaction
     */
    @Transactional
    public void recordMovement(
            UUID userId,
            UUID accountId,
            UUID movementId,
            LocalDate postedOn,
            long amountCents,
            String description,
            UUID categoryId,
            boolean transfer,
            String notes,
            boolean awaitingBankCopy) {
        Transaction transaction = new Transaction(
                userId, accountId, TransactionSource.MANUAL, null, postedOn, amountCents, description, null);
        transaction.setTransfer(transfer);
        transaction.setNotes(notes);
        if (categoryId != null) {
            categories.get(userId, categoryId);
            transaction.categorise(categoryId, CategorySource.USER);
        }
        transaction.linkToMovement(movementId, awaitingBankCopy);
        transactions.save(transaction);
    }

    /**
     * Removes recorded movements whose real transaction has now arrived from the bank, so nothing is
     * counted twice. Each recorded movement matches the closest new transaction for the same amount.
     */
    private void replaceRecordedMovements(UUID accountId, List<Transaction> arrived) {
        List<Transaction> waiting =
                new ArrayList<>(transactions.findByAccountIdAndAwaitingBankCopyTrueAndDeletedAtIsNull(accountId));
        if (waiting.isEmpty() || arrived.isEmpty()) {
            return;
        }
        List<Transaction> unmatched = new ArrayList<>(arrived);
        waiting.sort(Comparator.comparing(Transaction::getPostedOn));
        for (Transaction recorded : waiting) {
            unmatched.stream()
                    .filter(candidate -> candidate.getAmountCents() == recorded.getAmountCents())
                    .filter(candidate ->
                            Math.abs(ChronoUnit.DAYS.between(recorded.getPostedOn(), candidate.getPostedOn()))
                                    <= MATCH_WINDOW_DAYS)
                    .min(Comparator.comparingLong(candidate ->
                            Math.abs(ChronoUnit.DAYS.between(recorded.getPostedOn(), candidate.getPostedOn()))))
                    .ifPresent(bankCopy -> {
                        recorded.replaceWith(bankCopy, clock.instant());
                        unmatched.remove(bankCopy);
                    });
        }
    }

    /**
     * Transactions between two dates in the shape the finance engine expects.
     */
    @Transactional(readOnly = true)
    public List<TransactionRecord> records(UUID userId, LocalDate from, LocalDate to) {
        Map<UUID, Category> categoryById = categories.byId(userId);
        return transactions.findForAnalysis(userId, from, to).stream()
                .map(t -> new TransactionRecord(
                        t.getPostedOn(),
                        Money.ofCents(t.getAmountCents()),
                        Optional.ofNullable(t.getCategoryId())
                                .map(categoryById::get)
                                .map(Category::toRef)
                                .orElse(null),
                        t.getMerchant(),
                        t.getDescription(),
                        t.isTransfer()))
                .toList();
    }

    /**
     * How much an account gained, or lost when negative, between two dates inclusive.
     */
    @Transactional(readOnly = true)
    public Money netChange(UUID userId, UUID accountId, LocalDate from, LocalDate to) {
        return Money.ofCents(transactions.sumForAccount(userId, accountId, from, to));
    }

    @Transactional(readOnly = true)
    public Optional<LocalDate> firstTransactionDate(UUID userId) {
        return transactions.findFirstPostedOn(userId);
    }

    @Transactional(readOnly = true)
    public long count(UUID userId) {
        return transactions.countByUserIdAndDeletedAtIsNull(userId);
    }

    @EventListener
    void onCategoryDeleted(CategoryDeleted event) {
        transactions.clearCategory(event.userId(), event.categoryId());
    }

    private static void autoCategorise(Transaction transaction, ExternalTransaction item, CategoryMatcher matcher) {
        Optional<UUID> ruleMatch = matcher.match(item.merchant(), item.description());
        if (ruleMatch.isPresent()) {
            transaction.categorise(ruleMatch.get(), CategorySource.RULE);
        } else if (item.suggestedCategoryId() != null) {
            transaction.categorise(item.suggestedCategoryId(), CategorySource.PROVIDER);
        } else if (transaction.getCategorySource() != CategorySource.RULE) {
            transaction.categorise(null, null);
        }
    }

    private Transaction find(UUID userId, UUID id) {
        return transactions.findActive(id, userId).orElseThrow(() -> ApiException.notFound("Transaction"));
    }

    private List<TransactionResponse> toResponses(UUID userId, List<Transaction> page) {
        Map<UUID, Account> accountById = accounts.byId(userId);
        Map<UUID, Category> categoryById = categories.byId(userId);
        return page.stream()
                .map(t -> new TransactionResponse(
                        t.getId(),
                        t.getAccountId(),
                        Optional.ofNullable(accountById.get(t.getAccountId()))
                                .map(Account::getName)
                                .orElse(null),
                        t.getPostedOn(),
                        MoneyResponse.of(t.getAmountCents()),
                        t.getDescription(),
                        t.getMerchant(),
                        CategoryResponse.from(t.getCategoryId() == null ? null : categoryById.get(t.getCategoryId())),
                        t.getCategorySource(),
                        t.getSource(),
                        t.isTransfer(),
                        t.getNotes(),
                        t.getCreatedAt()))
                .toList();
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
