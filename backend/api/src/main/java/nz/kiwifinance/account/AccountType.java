package nz.kiwifinance.account;

public enum AccountType {
    EVERYDAY(true),
    SAVINGS(true),
    CREDIT_CARD(false),
    LOAN(false),
    KIWISAVER(false),
    INVESTMENT(false),
    CASH(true),
    OTHER(false);

    private final boolean liquidByDefault;

    AccountType(boolean liquidByDefault) {
        this.liquidByDefault = liquidByDefault;
    }

    /**
     * Whether money in this kind of account can normally be spent at short notice.
     */
    public boolean isLiquidByDefault() {
        return liquidByDefault;
    }
}
