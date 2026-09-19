package nz.kiwifinance.income;

import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import nz.kiwifinance.common.web.CurrentUser;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
class IncomeController {

    private final IncomeService incomeService;

    @GetMapping("/api/v1/income-sources")
    IncomeResponses.Summary list(CurrentUser user) {
        return incomeService.summary(user.id());
    }

    @PostMapping("/api/v1/income-sources")
    @ResponseStatus(HttpStatus.CREATED)
    IncomeResponses.Source create(CurrentUser user, @Valid @RequestBody IncomeRequests.Save request) {
        return incomeService.create(user.id(), request);
    }

    @PutMapping("/api/v1/income-sources/{id}")
    IncomeResponses.Source update(
            CurrentUser user, @PathVariable UUID id, @Valid @RequestBody IncomeRequests.Save request) {
        return incomeService.update(user.id(), id, request);
    }

    @DeleteMapping("/api/v1/income-sources/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void delete(CurrentUser user, @PathVariable UUID id) {
        incomeService.delete(user.id(), id);
    }

    @PostMapping("/api/v1/income/pay-calculator")
    IncomeResponses.Pay calculate(@Valid @RequestBody IncomeRequests.PayCalculation request) {
        return incomeService.calculate(request);
    }
}
