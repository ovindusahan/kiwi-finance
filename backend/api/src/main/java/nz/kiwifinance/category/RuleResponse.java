package nz.kiwifinance.category;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

@Schema(name = "CategorisationRule")
record RuleResponse(UUID id, MatchType matchType, String pattern, UUID categoryId, int priority) {

    static RuleResponse from(CategorisationRule rule) {
        return new RuleResponse(
                rule.getId(), rule.getMatchType(), rule.getPattern(), rule.getCategoryId(), rule.getPriority());
    }
}
