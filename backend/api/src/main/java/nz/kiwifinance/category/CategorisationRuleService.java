package nz.kiwifinance.category;

import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import nz.kiwifinance.common.error.ApiException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CategorisationRuleService {

    private final CategorisationRuleRepository rules;
    private final CategoryService categories;

    @Transactional(readOnly = true)
    public CategoryMatcher matcherFor(UUID userId) {
        return new CategoryMatcher(rules.findByUserIdOrderByPriorityDescCreatedAtAsc(userId));
    }

    @Transactional(readOnly = true)
    List<CategorisationRule> list(UUID userId) {
        return rules.findByUserIdOrderByPriorityDescCreatedAtAsc(userId);
    }

    @Transactional
    CategorisationRule create(UUID userId, RuleRequests.Create request) {
        categories.get(userId, request.categoryId());
        return rules.save(new CategorisationRule(
                userId, request.matchType(), request.pattern().trim(), request.categoryId(), request.priority()));
    }

    @Transactional
    CategorisationRule update(UUID userId, UUID ruleId, RuleRequests.Update request) {
        CategorisationRule rule =
                rules.findByIdAndUserId(ruleId, userId).orElseThrow(() -> ApiException.notFound("Rule"));
        if (request.categoryId() != null) {
            categories.get(userId, request.categoryId());
        }
        rule.update(
                request.matchType(),
                request.pattern() == null ? null : request.pattern().trim(),
                request.categoryId(),
                request.priority());
        return rule;
    }

    @Transactional
    void delete(UUID userId, UUID ruleId) {
        rules.delete(rules.findByIdAndUserId(ruleId, userId).orElseThrow(() -> ApiException.notFound("Rule")));
    }
}
