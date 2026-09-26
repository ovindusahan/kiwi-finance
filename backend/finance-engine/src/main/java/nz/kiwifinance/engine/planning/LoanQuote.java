package nz.kiwifinance.engine.planning;

import java.math.BigDecimal;
import nz.kiwifinance.engine.explain.Explanation;
import nz.kiwifinance.engine.money.Money;

public record LoanQuote(
        Money principal,
        BigDecimal annualRate,
        int termMonths,
        Money fees,
        Money monthlyRepayment,
        Money weeklyRepayment,
        Money fortnightlyRepayment,
        Money totalRepaid,
        Money totalInterest,
        Explanation explanation) {}
