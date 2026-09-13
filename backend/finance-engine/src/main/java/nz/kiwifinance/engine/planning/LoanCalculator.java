package nz.kiwifinance.engine.planning;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import nz.kiwifinance.engine.explain.Explanation;
import nz.kiwifinance.engine.money.Money;
import nz.kiwifinance.engine.time.PayFrequency;

/**
 * Standard amortising loan with fixed monthly repayments. Fees are added to the amount borrowed,
 * as most New Zealand car and personal lenders do.
 */
public final class LoanCalculator {

    public LoanQuote quote(Money amount, BigDecimal annualRate, int termMonths, Money fees) {
        if (termMonths <= 0) {
            throw new IllegalArgumentException("Term must be at least one month");
        }
        if (annualRate.signum() < 0) {
            throw new IllegalArgumentException("Interest rate cannot be negative");
        }
        Money principal = amount.plus(fees);
        Money monthly = monthlyRepayment(principal, annualRate, termMonths);
        Money totalRepaid = monthly.times(termMonths);
        Money totalInterest = Money.max(Money.ZERO, totalRepaid.minus(principal));
        Money weekly = PayFrequency.MONTHLY.convert(monthly, PayFrequency.WEEKLY);
        Money fortnightly = PayFrequency.MONTHLY.convert(monthly, PayFrequency.FORTNIGHTLY);

        var explanation = Explanation.builder()
                .summary("Borrowing %s over %s costs %s a month and %s in interest%s."
                        .formatted(
                                amount.formatWhole(),
                                term(termMonths),
                                monthly.format(),
                                totalInterest.formatWhole(),
                                fees.isPositive() ? " plus %s in fees".formatted(fees.formatWhole()) : ""))
                .step(
                        "Amount borrowed",
                        principal.format(),
                        fees.isPositive() ? "Includes %s of fees.".formatted(fees.format()) : null)
                .step("Interest rate", percent(annualRate) + " a year")
                .step("Term", term(termMonths))
                .step(
                        "Monthly repayment",
                        monthly.format(),
                        "About %s a week or %s a fortnight.".formatted(weekly.format(), fortnightly.format()))
                .step("Total repaid", totalRepaid.format())
                .step("Total interest", totalInterest.format())
                .assumption("fixed_rate", "Fixed rate", "The interest rate stays the same for the whole term.", null)
                .build();

        return new LoanQuote(
                amount,
                annualRate,
                termMonths,
                fees,
                monthly,
                weekly,
                fortnightly,
                totalRepaid,
                totalInterest,
                explanation);
    }

    static Money monthlyRepayment(Money principal, BigDecimal annualRate, int months) {
        if (annualRate.signum() == 0) {
            return principal.dividedBy(BigDecimal.valueOf(months), RoundingMode.CEILING);
        }
        BigDecimal rate = annualRate.divide(BigDecimal.valueOf(12), MathContext.DECIMAL64);
        BigDecimal growth = BigDecimal.ONE.add(rate).pow(months, MathContext.DECIMAL64);
        BigDecimal payment = BigDecimal.valueOf(principal.cents())
                .multiply(rate)
                .multiply(growth)
                .divide(growth.subtract(BigDecimal.ONE), MathContext.DECIMAL64);
        return Money.ofCents(payment.setScale(0, RoundingMode.HALF_UP).longValueExact());
    }

    private static String term(int months) {
        if (months % 12 == 0) {
            int years = months / 12;
            return years == 1 ? "1 year" : years + " years";
        }
        return months + " months";
    }

    static String percent(BigDecimal rate) {
        return rate.multiply(BigDecimal.valueOf(100))
                        .setScale(2, RoundingMode.HALF_UP)
                        .stripTrailingZeros()
                        .toPlainString()
                + "%";
    }
}
