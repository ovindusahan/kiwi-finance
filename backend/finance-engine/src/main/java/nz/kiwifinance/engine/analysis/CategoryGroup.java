package nz.kiwifinance.engine.analysis;

public enum CategoryGroup {
    INCOME,
    ESSENTIALS,
    LIFESTYLE,
    SAVINGS,
    DEBT,
    TRANSFERS;

    public boolean isSpending() {
        return this == ESSENTIALS || this == LIFESTYLE || this == DEBT;
    }

    /**
     * Spending that has to continue even if income stops, which is what an emergency fund covers.
     */
    public boolean isEssential() {
        return this == ESSENTIALS || this == DEBT;
    }
}
