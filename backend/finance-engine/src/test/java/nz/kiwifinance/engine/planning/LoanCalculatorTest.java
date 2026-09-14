package nz.kiwifinance.engine.planning;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import nz.kiwifinance.engine.money.Money;
import org.junit.jupiter.api.Test;

class LoanCalculatorTest {

    private final LoanCalculator calculator = new LoanCalculator();

    @Test
    void calculatesAmortisingRepayments() {
        var quote = calculator.quote(Money.ofDollars(20_000), new BigDecimal("0.099"), 60, Money.ZERO);

        assertThat(quote.monthlyRepayment()).isEqualTo(Money.ofCents(42_396));
        assertThat(quote.totalRepaid()).isEqualTo(Money.ofCents(42_396 * 60));
        assertThat(quote.totalInterest()).isEqualTo(Money.ofCents(42_396 * 60 - 2_000_000));
        assertThat(quote.explanation().summary()).contains("5 years");
    }

    @Test
    void addsFeesToTheAmountBorrowed() {
        var quote = calculator.quote(Money.ofDollars(1_000), BigDecimal.ZERO, 10, Money.ofDollars(200));

        assertThat(quote.monthlyRepayment()).isEqualTo(Money.ofDollars(120));
        assertThat(quote.totalInterest()).isEqualTo(Money.ZERO);
    }
}
