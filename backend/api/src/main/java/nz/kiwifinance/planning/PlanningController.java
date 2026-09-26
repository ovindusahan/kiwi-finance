package nz.kiwifinance.planning;

import jakarta.validation.Valid;
import java.util.List;
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
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/planning")
@RequiredArgsConstructor
class PlanningController {

    private final PlanningService planningService;
    private final PurchaseImpactService purchaseImpactService;

    @PostMapping("/affordability")
    PlanningResponses.Affordability affordability(
            CurrentUser user, @Valid @RequestBody PlanningRequests.Purchase request) {
        return planningService.assess(user.id(), request);
    }

    /**
     * What buying a car, a house or another big item would cost, and what it would do to the
     * person's budget, savings and goals.
     */
    @PostMapping("/purchase-impact")
    PlanningResponses.Impact purchaseImpact(CurrentUser user, @Valid @RequestBody PlanningRequests.Impact request) {
        return purchaseImpactService.assess(user.id(), request);
    }

    @PostMapping("/loan")
    PlanningResponses.Loan loan(@Valid @RequestBody PlanningRequests.Loan request) {
        return planningService.loan(request);
    }

    @GetMapping("/purchase-plans")
    List<PlanningResponses.PurchasePlan> plans(CurrentUser user) {
        return planningService.plans(user.id());
    }

    @PostMapping("/purchase-plans")
    @ResponseStatus(HttpStatus.CREATED)
    PlanningResponses.PurchasePlan create(CurrentUser user, @Valid @RequestBody PlanningRequests.Purchase request) {
        return planningService.save(user.id(), null, request);
    }

    @GetMapping("/purchase-plans/{id}")
    PlanningResponses.PurchasePlan get(CurrentUser user, @PathVariable UUID id) {
        return planningService.plan(user.id(), id);
    }

    @PutMapping("/purchase-plans/{id}")
    PlanningResponses.PurchasePlan update(
            CurrentUser user, @PathVariable UUID id, @Valid @RequestBody PlanningRequests.Purchase request) {
        return planningService.save(user.id(), id, request);
    }

    @DeleteMapping("/purchase-plans/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void delete(CurrentUser user, @PathVariable UUID id) {
        planningService.delete(user.id(), id);
    }

    @PostMapping("/purchase-plans/{id}/goal")
    PlanningResponses.PurchasePlan startSaving(CurrentUser user, @PathVariable UUID id) {
        return planningService.startSaving(user.id(), id);
    }
}
