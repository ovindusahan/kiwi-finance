package nz.kiwifinance.bankfeed.akahu;

import io.swagger.v3.oas.annotations.media.Schema;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import nz.kiwifinance.bankfeed.BankConnection;
import nz.kiwifinance.bankfeed.BankConnectionService;
import nz.kiwifinance.bankfeed.BankFeedException;
import nz.kiwifinance.bankfeed.BankFeedProvider;
import nz.kiwifinance.bankfeed.ConnectionMethod;
import nz.kiwifinance.bankfeed.FeedAccount;
import nz.kiwifinance.bankfeed.OAuthStates;
import nz.kiwifinance.common.error.ApiException;
import nz.kiwifinance.common.error.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * Connects a person's own Akahu account, either with personal-app tokens they paste in or through
 * Akahu's OAuth consent flow. Tokens are checked against Akahu before anything is saved.
 */
@Service
@RequiredArgsConstructor
class AkahuConnectionService {

    static final Duration OAUTH_STATE_TTL = Duration.ofMinutes(10);
    private static final int STATE_BYTES = 32;

    private final AkahuApi api;
    private final AkahuProperties properties;
    private final BankConnectionService connections;
    private final OAuthStates oauthStates;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();

    BankConnection connectPersonalApp(UUID userId, AkahuRequests.PersonalTokens tokens) {
        if (connections.current(userId, BankFeedProvider.AKAHU).isPresent()) {
            throw new ApiException(
                    ErrorCode.BANK_CONNECTION_EXISTS,
                    "Akahu is already connected. To use new tokens, update the existing connection.");
        }
        var credentials = new AkahuCredentials(tokens.appToken(), tokens.userToken());
        Verified verified = verify(credentials, true);
        return connections.establish(
                userId,
                BankFeedProvider.AKAHU,
                ConnectionMethod.PERSONAL_APP,
                verified.akahuUserId(),
                credentials,
                verified.accounts());
    }

    /**
     * Replaces the tokens on an existing connection, for example after the person regenerated them
     * in Akahu. The tokens must belong to the same Akahu account.
     */
    BankConnection updatePersonalAppTokens(UUID userId, UUID connectionId, AkahuRequests.PersonalTokens tokens) {
        BankConnection existing = connections.get(userId, connectionId);
        var credentials = new AkahuCredentials(tokens.appToken(), tokens.userToken());
        Verified verified = verify(credentials, true);
        if (!verified.akahuUserId().equals(existing.getExternalUserId())) {
            throw new ApiException(
                    ErrorCode.BANK_CONNECTION_DIFFERENT_USER,
                    "Those tokens belong to a different Akahu account. Disconnect first to switch accounts.");
        }
        return connections.establish(
                userId,
                BankFeedProvider.AKAHU,
                ConnectionMethod.PERSONAL_APP,
                verified.akahuUserId(),
                credentials,
                verified.accounts());
    }

    OAuthStart startOAuth(UUID userId) {
        requireOAuth();
        byte[] bytes = new byte[STATE_BYTES];
        random.nextBytes(bytes);
        String state = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        Instant expiresAt = clock.instant().plus(OAUTH_STATE_TTL);
        oauthStates.issue(userId, BankFeedProvider.AKAHU, hash(state), expiresAt);
        String url = UriComponentsBuilder.fromUriString(properties.oauthUrl())
                .queryParam("response_type", "code")
                .queryParam("client_id", properties.appToken())
                .queryParam("redirect_uri", properties.redirectUri())
                .queryParam("scope", "ENDURING_CONSENT")
                .queryParam("state", state)
                .encode()
                .build()
                .toUriString();
        return new OAuthStart(url, expiresAt);
    }

    BankConnection completeOAuth(UUID userId, AkahuRequests.OAuthCallback callback) {
        requireOAuth();
        if (!oauthStates.consume(userId, BankFeedProvider.AKAHU, hash(callback.state()))) {
            throw new ApiException(
                    ErrorCode.OAUTH_STATE_INVALID,
                    "This authorisation link has expired. Please start connecting again.");
        }
        AkahuModels.Token token;
        try {
            token = api.exchangeCode(callback.code());
        } catch (BankFeedException e) {
            throw e.reason() == BankFeedException.Reason.UNAUTHORISED
                    ? new ApiException(
                            ErrorCode.OAUTH_STATE_INVALID, "Akahu didn't accept the authorisation. Please try again.")
                    : unavailable(e);
        }
        if (token.accessToken() == null) {
            throw new ApiException(
                    ErrorCode.AKAHU_UNAVAILABLE, "Akahu didn't return an access token. Please try again.");
        }
        var stored = new AkahuCredentials(null, token.accessToken());
        Verified verified = verify(stored.withAppToken(properties.appToken()), false);
        return connections.establish(
                userId,
                BankFeedProvider.AKAHU,
                ConnectionMethod.OAUTH,
                verified.akahuUserId(),
                stored,
                verified.accounts());
    }

    boolean oauthAvailable() {
        return properties.oauthConfigured();
    }

    private void requireOAuth() {
        if (!properties.oauthConfigured()) {
            throw new ApiException(
                    ErrorCode.AKAHU_OAUTH_NOT_CONFIGURED,
                    "One-click Akahu connections aren't set up on this server. Use your own Akahu personal app instead.");
        }
    }

    private Verified verify(AkahuCredentials credentials, boolean personalApp) {
        try {
            AkahuModels.User user = api.me(credentials);
            List<FeedAccount> accounts = api.accounts(credentials).stream()
                    .map(AkahuMapper::toFeedAccount)
                    .toList();
            return new Verified(user.id(), accounts);
        } catch (BankFeedException e) {
            if (e.reason() == BankFeedException.Reason.UNAUTHORISED) {
                throw new ApiException(
                        ErrorCode.AKAHU_TOKENS_REJECTED,
                        personalApp
                                ? "Akahu didn't accept those tokens. Copy both tokens again from the Developers page in Akahu."
                                : "Akahu didn't accept the connection. Please try connecting again.");
            }
            throw unavailable(e);
        }
    }

    private static ApiException unavailable(BankFeedException e) {
        return new ApiException(
                ErrorCode.AKAHU_UNAVAILABLE, "We couldn't reach Akahu just now. Please try again shortly.", e);
    }

    static String hash(String value) {
        try {
            return HexFormat.of()
                    .formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is unavailable", e);
        }
    }

    record Verified(String akahuUserId, List<FeedAccount> accounts) {}

    @Schema(name = "AkahuOAuthStart")
    record OAuthStart(String authorisationUrl, Instant expiresAt) {}
}
