package nz.kiwifinance.transaction;

/**
 * Who chose a transaction's category. Automatic sources never overwrite a person's choice.
 */
public enum CategorySource {
    USER,
    RULE,
    PROVIDER
}
