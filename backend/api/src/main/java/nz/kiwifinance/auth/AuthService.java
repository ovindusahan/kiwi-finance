package nz.kiwifinance.auth;

import java.time.Clock;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;
import nz.kiwifinance.common.error.ApiException;
import nz.kiwifinance.common.error.ErrorCode;
import nz.kiwifinance.common.persistence.Ids;
import nz.kiwifinance.common.security.AuthProperties;
import nz.kiwifinance.user.ProfileService;
import nz.kiwifinance.user.User;
import nz.kiwifinance.user.UserRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class AuthService {

    private final UserRepository users;
    private final RefreshTokenRepository refreshTokens;
    private final ProfileService profiles;
    private final TokenService tokens;
    private final PasswordEncoder passwordEncoder;
    private final AuthProperties properties;
    private final ApplicationEventPublisher events;
    private final Clock clock;
    private final String dummyHash;

    AuthService(
            UserRepository users,
            RefreshTokenRepository refreshTokens,
            ProfileService profiles,
            TokenService tokens,
            PasswordEncoder passwordEncoder,
            AuthProperties properties,
            ApplicationEventPublisher events,
            Clock clock) {
        this.users = users;
        this.refreshTokens = refreshTokens;
        this.profiles = profiles;
        this.tokens = tokens;
        this.passwordEncoder = passwordEncoder;
        this.properties = properties;
        this.events = events;
        this.clock = clock;
        this.dummyHash = passwordEncoder.encode(UUID.randomUUID().toString());
    }

    @Transactional
    TokenPair register(AuthRequests.Register request) {
        String email = normalise(request.email());
        if (users.existsByEmail(email)) {
            throw new ApiException(ErrorCode.EMAIL_ALREADY_REGISTERED, "An account with this email already exists.");
        }
        User user = users.save(new User(
                email,
                passwordEncoder.encode(request.password()),
                request.displayName().trim()));
        profiles.createDefault(user.getId());
        return tokens.issue(user.getId(), Ids.newId());
    }

    /**
     * Failed attempts are recorded even though the method throws, so the lockout counter survives
     * the rollback that would otherwise undo it.
     */
    @Transactional(noRollbackFor = ApiException.class)
    TokenPair login(AuthRequests.Login request) {
        Instant now = clock.instant();
        User user = users.findByEmail(normalise(request.email())).orElse(null);
        if (user == null) {
            // Hash anyway so response time does not reveal whether the email is registered.
            passwordEncoder.matches(request.password(), dummyHash);
            throw invalidCredentials();
        }
        if (user.isLocked(now)) {
            throw new ApiException(
                    ErrorCode.TOO_MANY_LOGIN_ATTEMPTS, "Too many unsuccessful attempts. Try again in a few minutes.");
        }
        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            user.recordFailedLogin(now, properties.maxFailedLogins(), properties.lockoutDuration());
            throw invalidCredentials();
        }
        user.recordSuccessfulLogin();
        return tokens.issue(user.getId(), Ids.newId());
    }

    @Transactional(noRollbackFor = ApiException.class)
    TokenPair refresh(String refreshToken) {
        Instant now = clock.instant();
        RefreshToken current = refreshTokens
                .findByTokenHash(TokenService.hash(refreshToken))
                .orElseThrow(AuthService::invalidRefreshToken);
        if (current.isRevoked()) {
            // A rotated token was used again, so it has probably been stolen. End every session
            // descended from the same login.
            refreshTokens.revokeFamily(current.getFamilyId(), now);
            throw invalidRefreshToken();
        }
        if (current.isExpired(now)) {
            throw invalidRefreshToken();
        }
        TokenPair next = tokens.issue(current.getUserId(), current.getFamilyId());
        current.rotate(next.refreshTokenId(), now);
        return next;
    }

    @Transactional
    void logout(String refreshToken) {
        refreshTokens
                .findByTokenHash(TokenService.hash(refreshToken))
                .ifPresent(token -> refreshTokens.revokeFamily(token.getFamilyId(), clock.instant()));
    }

    @Transactional(readOnly = true)
    User me(UUID userId) {
        return users.findById(userId)
                .orElseThrow(() -> new ApiException(ErrorCode.UNAUTHENTICATED, "Sign in to continue."));
    }

    @Transactional
    User updateAccount(UUID userId, AuthRequests.UpdateAccount request) {
        User user = me(userId);
        if (request.displayName() != null && !request.displayName().isBlank()) {
            user.rename(request.displayName().trim());
        }
        if (request.email() != null && !request.email().isBlank()) {
            String email = normalise(request.email());
            if (!email.equals(user.getEmail())) {
                checkPassword(user, request.currentPassword());
                if (users.existsByEmail(email)) {
                    throw new ApiException(
                            ErrorCode.EMAIL_ALREADY_REGISTERED, "An account with this email already exists.");
                }
                user.changeEmail(email);
            }
        }
        return user;
    }

    /**
     * Changes the password and ends every other session, then starts a fresh one for the device
     * that made the change.
     */
    @Transactional
    TokenPair changePassword(UUID userId, AuthRequests.ChangePassword request) {
        User user = me(userId);
        checkPassword(user, request.currentPassword());
        user.changePassword(passwordEncoder.encode(request.newPassword()));
        refreshTokens.revokeAllForUser(userId, clock.instant());
        return tokens.issue(userId, Ids.newId());
    }

    private void checkPassword(User user, String password) {
        if (password == null || !passwordEncoder.matches(password, user.getPasswordHash())) {
            throw new ApiException(ErrorCode.WRONG_PASSWORD, "That password is not correct.");
        }
    }

    @Transactional
    void deleteAccount(UUID userId, String password) {
        User user = me(userId);
        if (!passwordEncoder.matches(password, user.getPasswordHash())) {
            throw new ApiException(ErrorCode.INVALID_CREDENTIALS, "That password is not correct.");
        }
        events.publishEvent(new UserDeletionRequested(userId));
        users.delete(user);
    }

    private static String normalise(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private static ApiException invalidCredentials() {
        return new ApiException(ErrorCode.INVALID_CREDENTIALS, "Email or password is incorrect.");
    }

    private static ApiException invalidRefreshToken() {
        return new ApiException(ErrorCode.INVALID_REFRESH_TOKEN, "Your session has ended. Please sign in again.");
    }
}
