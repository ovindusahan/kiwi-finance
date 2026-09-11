package nz.kiwifinance.engine.nzrules;

import java.time.LocalDate;
import java.util.List;
import java.util.NavigableMap;
import java.util.TreeMap;
import nz.kiwifinance.engine.time.TaxYear;

/**
 * Resolves the rule set that applies on a date. Dates outside the published years use the
 * nearest published year, and callers state that as an assumption via {@link #isPublished}.
 */
public final class NzRules {

    private static final NzRules STANDARD = new NzRules(List.of(RuleSet2025To26.RULES, RuleSet2026To27.RULES));

    private final NavigableMap<TaxYear, NzRuleSet> ruleSets = new TreeMap<>();

    public NzRules(List<NzRuleSet> ruleSets) {
        if (ruleSets.isEmpty()) {
            throw new IllegalArgumentException("At least one rule set is required");
        }
        ruleSets.forEach(rules -> this.ruleSets.put(rules.taxYear(), rules));
    }

    public static NzRules standard() {
        return STANDARD;
    }

    public NzRuleSet forDate(LocalDate date) {
        return forTaxYear(TaxYear.containing(date));
    }

    public NzRuleSet forTaxYear(TaxYear taxYear) {
        var exact = ruleSets.get(taxYear);
        if (exact != null) {
            return exact;
        }
        return taxYear.compareTo(ruleSets.firstKey()) < 0
                ? ruleSets.firstEntry().getValue()
                : ruleSets.lastEntry().getValue();
    }

    public boolean isPublished(TaxYear taxYear) {
        return ruleSets.containsKey(taxYear);
    }

    public NzRuleSet latest() {
        return ruleSets.lastEntry().getValue();
    }
}
