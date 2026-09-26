package nz.kiwifinance.analysis;

/**
 * Where a person's typical income figure comes from.
 */
public enum IncomeBasis {
    /** Median income seen in their transactions. */
    TRANSACTIONS,
    /** The income sources they entered, because there isn't enough transaction history. */
    INCOME_SOURCES,
    /** No income information yet. */
    NONE
}
