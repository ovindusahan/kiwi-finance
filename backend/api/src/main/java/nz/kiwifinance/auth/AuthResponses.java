package nz.kiwifinance.auth;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;
import nz.kiwifinance.user.User;

final class AuthResponses {

    private AuthResponses() {}

    @Schema(name = "AuthTokens")
    record Tokens(
            String accessToken,
            String tokenType,
            long expiresIn,
            Instant accessTokenExpiresAt,
            String refreshToken,
            Instant refreshTokenExpiresAt) {

        static Tokens from(TokenPair pair, Instant now) {
            return new Tokens(
                    pair.accessToken(),
                    "Bearer",
                    pair.accessTokenExpiresAt().getEpochSecond() - now.getEpochSecond(),
                    pair.accessTokenExpiresAt(),
                    pair.refreshToken(),
                    pair.refreshTokenExpiresAt());
        }
    }

    @Schema(name = "AuthenticatedUser")
    record Me(UUID id, String email, String displayName, Instant createdAt) {

        static Me from(User user) {
            return new Me(user.getId(), user.getEmail(), user.getDisplayName(), user.getCreatedAt());
        }
    }
}
