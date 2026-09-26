package nz.kiwifinance.budget;

import jakarta.validation.Valid;
import java.time.YearMonth;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import nz.kiwifinance.common.error.ApiException;
import nz.kiwifinance.common.web.CurrentUser;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/budgets")
@RequiredArgsConstructor
class BudgetController {

    private final BudgetService budgetService;

    @GetMapping("/recommendation")
    BudgetResponses.Recommendation recommendation(CurrentUser user) {
        return budgetService.recommend(user.id());
    }

    @GetMapping("/current")
    BudgetResponses.Current current(
            CurrentUser user, @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM") YearMonth month) {
        return budgetService.current(user.id(), month).orElseThrow(() -> ApiException.notFound("Budget"));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    BudgetResponses.Current create(CurrentUser user, @Valid @RequestBody BudgetRequests.Save request) {
        return budgetService.create(user.id(), request);
    }

    @PutMapping("/{id}")
    BudgetResponses.Current update(
            CurrentUser user, @PathVariable UUID id, @Valid @RequestBody BudgetRequests.Save request) {
        return budgetService.update(user.id(), id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void delete(CurrentUser user, @PathVariable UUID id) {
        budgetService.delete(user.id(), id);
    }
}
