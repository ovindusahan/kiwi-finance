package nz.kiwifinance.account;

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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/accounts")
@RequiredArgsConstructor
class AccountController {

    private final AccountService accountService;

    @GetMapping
    List<AccountResponse> list(CurrentUser user, @RequestParam(defaultValue = "false") boolean includeArchived) {
        return accountService.list(user.id(), includeArchived).stream()
                .map(AccountResponse::from)
                .toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    AccountResponse create(CurrentUser user, @Valid @RequestBody AccountRequests.Create request) {
        return AccountResponse.from(accountService.create(user.id(), request));
    }

    @GetMapping("/{id}")
    AccountResponse get(CurrentUser user, @PathVariable UUID id) {
        return AccountResponse.from(accountService.get(user.id(), id));
    }

    @PatchMapping("/{id}")
    AccountResponse update(
            CurrentUser user, @PathVariable UUID id, @Valid @RequestBody AccountRequests.Update request) {
        return AccountResponse.from(accountService.update(user.id(), id, request));
    }

    /**
     * Archives rather than deletes, so the account's transaction history stays in reports.
     */
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void archive(CurrentUser user, @PathVariable UUID id) {
        accountService.archive(user.id(), id);
    }
}
