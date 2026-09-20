package nz.kiwifinance.category;

import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import nz.kiwifinance.common.web.CurrentUser;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/categorisation-rules")
@RequiredArgsConstructor
class CategorisationRuleController {

    private final CategorisationRuleService ruleService;

    @GetMapping
    List<RuleResponse> list(CurrentUser user) {
        return ruleService.list(user.id()).stream().map(RuleResponse::from).toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    RuleResponse create(CurrentUser user, @Valid @RequestBody RuleRequests.Create request) {
        return RuleResponse.from(ruleService.create(user.id(), request));
    }

    @PatchMapping("/{id}")
    RuleResponse update(CurrentUser user, @PathVariable UUID id, @Valid @RequestBody RuleRequests.Update request) {
        return RuleResponse.from(ruleService.update(user.id(), id, request));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void delete(CurrentUser user, @PathVariable UUID id) {
        ruleService.delete(user.id(), id);
    }
}
