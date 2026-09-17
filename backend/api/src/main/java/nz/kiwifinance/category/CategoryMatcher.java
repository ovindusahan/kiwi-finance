package nz.kiwifinance.category;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * A person's categorisation rules, loaded once and applied to many transactions.
 */
public final class CategoryMatcher {

    private final List<CategorisationRule> rules;

    CategoryMatcher(List<CategorisationRule> rules) {
        this.rules = List.copyOf(rules);
    }

    public Optional<UUID> match(String merchant, String description) {
        return rules.stream()
                .filter(rule -> rule.matches(merchant, description))
                .map(CategorisationRule::getCategoryId)
                .findFirst();
    }
}
