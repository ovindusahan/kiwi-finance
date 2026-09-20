package nz.kiwifinance.transaction;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import nz.kiwifinance.common.web.CurrentUser;
import nz.kiwifinance.common.web.PageResponse;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/transactions")
@RequiredArgsConstructor
class TransactionController {

    private final TransactionService transactionService;

    @GetMapping
    PageResponse<TransactionResponse> list(
            CurrentUser user,
            @RequestParam(required = false) UUID accountId,
            @RequestParam(required = false) UUID categoryId,
            @RequestParam(defaultValue = "false") boolean uncategorised,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) TransactionFilter.Direction direction,
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "50") int limit) {
        var filter = new TransactionFilter(accountId, categoryId, uncategorised, from, to, search, direction);
        return transactionService.list(user.id(), filter, cursor, limit);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    TransactionResponse create(CurrentUser user, @Valid @RequestBody TransactionRequests.Create request) {
        return transactionService.create(user.id(), request);
    }

    @GetMapping("/{id}")
    TransactionResponse get(CurrentUser user, @PathVariable UUID id) {
        return transactionService.get(user.id(), id);
    }

    @PatchMapping("/{id}")
    TransactionResponse update(
            CurrentUser user, @PathVariable UUID id, @Valid @RequestBody TransactionRequests.Update request) {
        return transactionService.update(user.id(), id, request);
    }

    @PutMapping("/{id}/category")
    TransactionResponse setCategory(
            CurrentUser user, @PathVariable UUID id, @RequestBody TransactionRequests.SetCategory request) {
        return transactionService.setCategory(user.id(), id, request.categoryId());
    }

    @PostMapping("/categorise")
    CategoriseResponse categorise(CurrentUser user, @Valid @RequestBody TransactionRequests.Categorise request) {
        return new CategoriseResponse(
                transactionService.categorise(user.id(), request.transactionIds(), request.categoryId()));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void delete(CurrentUser user, @PathVariable UUID id) {
        transactionService.delete(user.id(), id);
    }

    @PostMapping("/recategorise")
    RecategoriseResponse recategorise(CurrentUser user) {
        return new RecategoriseResponse(transactionService.applyRules(user.id()));
    }

    @Schema(name = "RecategoriseResult")
    record RecategoriseResponse(int updated) {}

    @Schema(name = "CategoriseResult")
    record CategoriseResponse(int updated) {}
}
