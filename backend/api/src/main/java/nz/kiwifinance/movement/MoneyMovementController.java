package nz.kiwifinance.movement;

import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import nz.kiwifinance.common.web.CurrentUser;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/money-movements")
@RequiredArgsConstructor
class MoneyMovementController {

    private final MoneyMovementService movementService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    MovementResponse record(CurrentUser user, @Valid @RequestBody MovementRequest request) {
        return movementService.record(user.id(), request);
    }

    @GetMapping
    List<MovementResponse> recent(
            CurrentUser user,
            @RequestParam(required = false) UUID accountId,
            @RequestParam(defaultValue = "10") int limit) {
        return movementService.recent(user.id(), accountId, limit);
    }
}
