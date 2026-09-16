package nz.kiwifinance.user;

public enum EmploymentType {
    EMPLOYEE,
    SELF_EMPLOYED,
    CONTRACTOR,
    STUDENT,
    NOT_WORKING,
    RETIRED;

    /**
     * Whether income can stop without notice or redundancy pay, which calls for a larger
     * emergency fund.
     */
    public boolean hasIrregularWork() {
        return this == SELF_EMPLOYED || this == CONTRACTOR;
    }
}
