package nz.kiwifinance.common.security;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import java.util.Base64;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * @param jwtSecret base64-encoded HMAC key of at least 256 bits
 */
@Validated
@ConfigurationProperties("kiwi.auth")
public record AuthProperties(
        @NotBlank String jwtSecret,
        @NotBlank String issuer,
        @NotNull Duration accessTokenTtl,
        @NotNull Duration refreshTokenTtl,
        @Min(1) int maxFailedLogins,
        @NotNull Duration lockoutDuration) {

    private static final int MIN_KEY_BYTES = 32;

    public AuthProperties {
        if (jwtSecret != null && !jwtSecret.isBlank() && decode(jwtSecret).length < MIN_KEY_BYTES) {
            throw new IllegalArgumentException("kiwi.auth.jwt-secret must decode to at least 32 bytes");
        }
    }

    public byte[] jwtKeyBytes() {
        return decode(jwtSecret);
    }

    private static byte[] decode(String value) {
        try {
            return Base64.getDecoder().decode(value);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("kiwi.auth.jwt-secret must be base64-encoded", e);
        }
    }
}
