package nz.kiwifinance.engine.tax;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Objects;
import nz.kiwifinance.engine.money.Money;
import nz.kiwifinance.engine.nzrules.TaxCode;
import nz.kiwifinance.engine.time.PayFrequency;

/**
 * @param kiwiSaverRate the employee contribution rate, or {@code null} for non-members
 */
public record PayRequest(
        Money grossPay,
        PayFrequency frequency,
        TaxCode taxCode,
        boolean studentLoan,
        BigDecimal kiwiSaverRate,
        LocalDate payDate) {

    public PayRequest {
        Objects.requireNonNull(grossPay, "grossPay");
        Objects.requireNonNull(frequency, "frequency");
        Objects.requireNonNull(taxCode, "taxCode");
        Objects.requireNonNull(payDate, "payDate");
        if (grossPay.isNegative()) {
            throw new IllegalArgumentException("Gross pay cannot be negative");
        }
    }

    public boolean isKiwiSaverMember() {
        return kiwiSaverRate != null;
    }

    public PayRequest withGrossPay(Money gross) {
        return new PayRequest(gross, frequency, taxCode, studentLoan, kiwiSaverRate, payDate);
    }
}
