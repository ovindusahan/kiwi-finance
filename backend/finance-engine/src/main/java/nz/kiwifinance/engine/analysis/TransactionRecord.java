package nz.kiwifinance.engine.analysis;

import java.time.LocalDate;
import java.util.Objects;
import nz.kiwifinance.engine.money.Money;

/**
 * A transaction as the engine sees it. Negative amounts are money out.
 *
 * @param category the assigned category, or {@code null} if uncategorised
 */
public record TransactionRecord(
        LocalDate date, Money amount, CategoryRef category, String merchant, String description, boolean transfer) {

    public TransactionRecord {
        Objects.requireNonNull(date, "date");
        Objects.requireNonNull(amount, "amount");
        Objects.requireNonNull(description, "description");
    }

    public String counterparty() {
        return merchant != null && !merchant.isBlank() ? merchant : description;
    }

    public Flow flow() {
        if (transfer || (category != null && category.group() == CategoryGroup.TRANSFERS)) {
            return Flow.TRANSFER;
        }
        if (category == null) {
            return amount.isPositive() ? Flow.INCOME : Flow.SPENDING;
        }
        return switch (category.group()) {
            case INCOME -> Flow.INCOME;
            case SAVINGS -> Flow.SAVING;
            case ESSENTIALS, LIFESTYLE, DEBT -> Flow.SPENDING;
            case TRANSFERS -> Flow.TRANSFER;
        };
    }

    /**
     * How this transaction moves money, for cash flow purposes. Refunds in a spending category
     * are spending with a positive amount, so they reduce that category's total.
     */
    public enum Flow {
        INCOME,
        SPENDING,
        SAVING,
        TRANSFER
    }
}
