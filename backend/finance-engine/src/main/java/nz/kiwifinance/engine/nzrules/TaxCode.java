package nz.kiwifinance.engine.nzrules;

import lombok.Getter;

/**
 * Inland Revenue tax codes for salary and wages. Student loan variants ("M SL" and so on) are
 * modelled separately as a flag, because the student loan deduction is independent of the code.
 */
public enum TaxCode {
    /** Main income. */
    M(false),
    /** Main income, eligible for the Independent Earner Tax Credit. */
    ME(false),
    /** Secondary income, total income up to $15,600. */
    SB(true),
    /** Secondary income, total income $15,601 to $53,500. */
    S(true),
    /** Secondary income, total income $53,501 to $78,100. */
    SH(true),
    /** Secondary income, total income $78,101 to $180,000. */
    ST(true),
    /** Secondary income, total income over $180,000. */
    SA(true);

    @Getter
    private final boolean secondary;

    TaxCode(boolean secondary) {
        this.secondary = secondary;
    }
}
