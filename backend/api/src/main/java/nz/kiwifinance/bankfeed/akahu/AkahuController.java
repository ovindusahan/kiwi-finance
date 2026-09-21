package nz.kiwifinance.bankfeed.akahu;

import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import nz.kiwifinance.bankfeed.BankConnection;
import nz.kiwifinance.bankfeed.BankConnectionService;
import nz.kiwifinance.bankfeed.BankFeedResponses;
import nz.kiwifinance.common.web.CurrentUser;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/bank-feeds/akahu")
@RequiredArgsConstructor
class AkahuController {

    private final AkahuConnectionService akahu;
    private final AkahuSetupGuideService setupGuide;
    private final BankConnectionService connections;

    @GetMapping("/setup-guide")
    SetupGuide setupGuide(CurrentUser user) {
        return setupGuide.guideFor(user.id());
    }

    @PostMapping("/personal-connections")
    @ResponseStatus(HttpStatus.CREATED)
    BankFeedResponses.Connection connectPersonalApp(
            CurrentUser user, @Valid @RequestBody AkahuRequests.PersonalTokens tokens) {
        BankConnection connection = akahu.connectPersonalApp(user.id(), tokens);
        return connections.view(user.id(), connection.getId());
    }

    @PutMapping("/connections/{id}/credentials")
    BankFeedResponses.Connection updateTokens(
            CurrentUser user, @PathVariable UUID id, @Valid @RequestBody AkahuRequests.PersonalTokens tokens) {
        BankConnection connection = akahu.updatePersonalAppTokens(user.id(), id, tokens);
        return connections.view(user.id(), connection.getId());
    }

    @PostMapping("/oauth/authorisations")
    @ResponseStatus(HttpStatus.CREATED)
    AkahuConnectionService.OAuthStart startOAuth(CurrentUser user) {
        return akahu.startOAuth(user.id());
    }

    @PostMapping("/oauth/callback")
    @ResponseStatus(HttpStatus.CREATED)
    BankFeedResponses.Connection completeOAuth(
            CurrentUser user, @Valid @RequestBody AkahuRequests.OAuthCallback callback) {
        BankConnection connection = akahu.completeOAuth(user.id(), callback);
        return connections.view(user.id(), connection.getId());
    }
}
