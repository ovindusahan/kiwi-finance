package nz.kiwifinance.goal;

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
@RequestMapping("/api/v1/goals")
@RequiredArgsConstructor
class GoalController {

    private final GoalService goalService;
    private final GoalPlanService goalPlanService;

    @GetMapping
    List<GoalResponses.View> list(CurrentUser user) {
        return goalService.list(user.id());
    }

    /**
     * How to share what the person can save across their goals, with one suggestion per goal.
     */
    @GetMapping("/plan")
    GoalResponses.Plan plan(CurrentUser user) {
        return goalPlanService.plan(user.id());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    GoalResponses.View create(CurrentUser user, @Valid @RequestBody GoalRequests.Save request) {
        return goalService.create(user.id(), request);
    }

    @GetMapping("/{id}")
    GoalResponses.View get(CurrentUser user, @PathVariable UUID id) {
        return goalService.get(user.id(), id);
    }

    @PutMapping("/{id}")
    GoalResponses.View update(CurrentUser user, @PathVariable UUID id, @Valid @RequestBody GoalRequests.Save request) {
        return goalService.update(user.id(), id, request);
    }

    @PutMapping("/{id}/status")
    GoalResponses.View changeStatus(
            CurrentUser user, @PathVariable UUID id, @Valid @RequestBody GoalRequests.ChangeStatus request) {
        return goalService.changeStatus(user.id(), id, request.status());
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void delete(CurrentUser user, @PathVariable UUID id) {
        goalService.delete(user.id(), id);
    }

    @PostMapping("/{id}/contributions")
    @ResponseStatus(HttpStatus.CREATED)
    GoalResponses.View contribute(
            CurrentUser user, @PathVariable UUID id, @Valid @RequestBody GoalRequests.Contribute request) {
        return goalService.contribute(user.id(), id, request);
    }

    @GetMapping("/{id}/contributions")
    List<GoalResponses.Contribution> contributions(CurrentUser user, @PathVariable UUID id) {
        return goalService.contributions(user.id(), id);
    }
}
