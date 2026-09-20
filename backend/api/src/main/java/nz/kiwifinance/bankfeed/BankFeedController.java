package nz.kiwifinance.bankfeed;

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
@RequestMapping("/api/v1/bank-feeds/connections")
@RequiredArgsConstructor
class BankFeedController {

    private final BankConnectionService connections;
    private final BankSyncService syncs;

    @GetMapping
    List<BankFeedResponses.Connection> list(CurrentUser user) {
        return connections.list(user.id());
    }

    @GetMapping("/{id}")
    BankFeedResponses.Connection get(CurrentUser user, @PathVariable UUID id) {
        return connections.view(user.id(), id);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void disconnect(CurrentUser user, @PathVariable UUID id) {
        connections.disconnect(user.id(), id);
    }

    @PatchMapping("/{id}/accounts/{feedAccountId}")
    BankFeedResponses.FeedAccountView updateAccount(
            CurrentUser user,
            @PathVariable UUID id,
            @PathVariable UUID feedAccountId,
            @Valid @RequestBody BankFeedRequests.UpdateFeedAccount request) {
        return connections.updateFeedAccount(user.id(), id, feedAccountId, request);
    }

    @PostMapping("/{id}/syncs")
    @ResponseStatus(HttpStatus.ACCEPTED)
    BankFeedResponses.SyncRun sync(CurrentUser user, @PathVariable UUID id) {
        return BankFeedResponses.SyncRun.from(syncs.requestSync(user.id(), id, SyncTrigger.MANUAL));
    }

    @GetMapping("/{id}/syncs")
    List<BankFeedResponses.SyncRun> syncs(
            CurrentUser user, @PathVariable UUID id, @RequestParam(defaultValue = "10") int limit) {
        return syncs.recentRuns(user.id(), id, limit).stream()
                .map(BankFeedResponses.SyncRun::from)
                .toList();
    }

    @GetMapping("/{id}/syncs/{runId}")
    BankFeedResponses.SyncRun syncRun(CurrentUser user, @PathVariable UUID id, @PathVariable UUID runId) {
        return BankFeedResponses.SyncRun.from(syncs.run(user.id(), runId));
    }
}
