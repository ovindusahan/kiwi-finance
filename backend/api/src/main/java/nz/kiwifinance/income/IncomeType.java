package nz.kiwifinance.income;

public enum IncomeType {
    SALARY,
    WAGES,
    BENEFIT,
    SELF_EMPLOYED,
    OTHER;

    /**
     * Whether PAYE, ACC and KiwiSaver are deducted before the money is paid.
     */
    boolean isEmployment() {
        return this == SALARY || this == WAGES;
    }
}
