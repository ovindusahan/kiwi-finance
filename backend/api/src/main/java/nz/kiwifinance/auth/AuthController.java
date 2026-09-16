package nz.kiwifinance.auth;

import jakarta.validation.Valid;
import java.time.Clock;
import lombok.RequiredArgsConstructor;
import nz.kiwifinance.common.web.CurrentUser;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
class AuthController {

    private final AuthService authService;
    private final Clock clock;

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    AuthResponses.Tokens register(@Valid @RequestBody AuthRequests.Register request) {
        return AuthResponses.Tokens.from(authService.register(request), clock.instant());
    }

    @PostMapping("/login")
    AuthResponses.Tokens login(@Valid @RequestBody AuthRequests.Login request) {
        return AuthResponses.Tokens.from(authService.login(request), clock.instant());
    }

    @PostMapping("/refresh")
    AuthResponses.Tokens refresh(@Valid @RequestBody AuthRequests.Refresh request) {
        return AuthResponses.Tokens.from(authService.refresh(request.refreshToken()), clock.instant());
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void logout(@Valid @RequestBody AuthRequests.Logout request) {
        authService.logout(request.refreshToken());
    }

    @GetMapping("/me")
    AuthResponses.Me me(CurrentUser currentUser) {
        return AuthResponses.Me.from(authService.me(currentUser.id()));
    }

    @PatchMapping("/me")
    AuthResponses.Me updateAccount(CurrentUser currentUser, @Valid @RequestBody AuthRequests.UpdateAccount request) {
        return AuthResponses.Me.from(authService.updateAccount(currentUser.id(), request));
    }

    @PostMapping("/me/password")
    AuthResponses.Tokens changePassword(
            CurrentUser currentUser, @Valid @RequestBody AuthRequests.ChangePassword request) {
        return AuthResponses.Tokens.from(authService.changePassword(currentUser.id(), request), clock.instant());
    }

    @DeleteMapping("/me")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void deleteAccount(CurrentUser currentUser, @Valid @RequestBody AuthRequests.DeleteAccount request) {
        authService.deleteAccount(currentUser.id(), request.password());
    }
}
