package nz.kiwifinance.engine.nzrules;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import nz.kiwifinance.engine.money.Money;

/**
 * Values shared by several tax years, and the publications they come from.
 */
final class NzRuleSources {

    static final List<TaxBracket> INCOME_TAX_BRACKETS_FROM_31_JULY_2024 = List.of(
            TaxBracket.upTo(15_600, "0.105"),
            TaxBracket.upTo(53_500, "0.175"),
            TaxBracket.upTo(78_100, "0.30"),
            TaxBracket.upTo(180_000, "0.33"),
            TaxBracket.above("0.39"));

    static final Map<TaxCode, BigDecimal> SECONDARY_TAX_RATES = Map.of(
            TaxCode.SB, new BigDecimal("0.105"),
            TaxCode.S, new BigDecimal("0.175"),
            TaxCode.SH, new BigDecimal("0.30"),
            TaxCode.ST, new BigDecimal("0.33"),
            TaxCode.SA, new BigDecimal("0.39"));

    static final IndependentEarnerTaxCredit IETC_FROM_31_JULY_2024 = new IndependentEarnerTaxCredit(
            Money.ofDollars(520),
            Money.ofDollars(24_000),
            Money.ofDollars(66_000),
            Money.ofDollars(70_000),
            new BigDecimal("0.13"));

    static final List<TaxBracket> ESCT_BRACKETS = List.of(
            TaxBracket.upTo(18_720, "0.105"),
            TaxBracket.upTo(64_200, "0.175"),
            TaxBracket.upTo(93_720, "0.30"),
            TaxBracket.upTo(216_000, "0.33"),
            TaxBracket.above("0.39"));

    static final List<String> SOURCES = List.of(
            "https://www.ird.govt.nz/income-tax/income-tax-for-individuals/tax-codes-and-tax-rates-for-individuals/tax-rates-for-individuals",
            "https://www.acc.co.nz/for-business/understanding-levies-if-you-work",
            "https://www.ird.govt.nz/student-loans/living-in-new-zealand-with-a-student-loan/repaying-my-student-loan-when-i-earn-salary-or-wages",
            "https://www.ird.govt.nz/income-tax/income-tax-for-individuals/individual-tax-credits/independent-earner-tax-credit-ietc",
            "https://www.ird.govt.nz/kiwisaver",
            "https://www.ird.govt.nz/employing-staff/deductions-from-income/employer-superannuation-contribution-tax");

    private NzRuleSources() {}
}
