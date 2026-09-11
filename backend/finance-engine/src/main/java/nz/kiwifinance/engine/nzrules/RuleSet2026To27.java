package nz.kiwifinance.engine.nzrules;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import nz.kiwifinance.engine.money.Money;
import nz.kiwifinance.engine.time.TaxYear;

final class RuleSet2026To27 {

    // From 1 April 2026 the default KiwiSaver rate rose to 3.5%. Members can apply for a
    // temporary reduction to 3%, and employers may match that reduced rate.
    static final NzRuleSet RULES = new NzRuleSet(
            new TaxYear(2026),
            NzRuleSources.INCOME_TAX_BRACKETS_FROM_31_JULY_2024,
            NzRuleSources.SECONDARY_TAX_RATES,
            new AccEarnersLevy(new BigDecimal("0.0175"), Money.ofDollars(156_641)),
            new StudentLoanRules(new BigDecimal("0.12"), Money.ofDollars(24_128)),
            NzRuleSources.IETC_FROM_31_JULY_2024,
            new KiwiSaverRules(
                    List.of(
                            new BigDecimal("0.03"),
                            new BigDecimal("0.035"),
                            new BigDecimal("0.04"),
                            new BigDecimal("0.06"),
                            new BigDecimal("0.08"),
                            new BigDecimal("0.10")),
                    new BigDecimal("0.035"),
                    new BigDecimal("0.035"),
                    new BigDecimal("0.25"),
                    Money.ofDollars("260.72"),
                    Money.ofDollars(180_000),
                    3,
                    Money.ofDollars(1_000)),
            NzRuleSources.ESCT_BRACKETS,
            LocalDate.of(2026, 10, 1),
            NzRuleSources.SOURCES);

    private RuleSet2026To27() {}
}
