package nz.kiwifinance.bankfeed.akahu;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Akahu settings. {@code appToken}, {@code appSecret} and {@code redirectUri} are only needed for
 * OAuth connections, which require an app registered with Akahu. Personal-app connections work
 * without them because each person supplies their own tokens.
 */
@Validated
@ConfigurationProperties("kiwi.akahu")
public record AkahuProperties(
        @NotBlank String apiUrl,
        @NotBlank String oauthUrl,
        @NotBlank String portalUrl,
        String appToken,
        String appSecret,
        String redirectUri,
        @NotNull Duration connectTimeout,
        @NotNull Duration readTimeout,
        @Min(1) int initialHistoryDays,
        @Min(0) int resyncOverlapDays,
        @NotNull Duration syncLease,
        @Valid @NotNull ScheduledSync scheduledSync) {

    public record ScheduledSync(boolean enabled, @NotBlank String cron) {}

    public boolean oauthConfigured() {
        return isSet(appToken) && isSet(appSecret) && isSet(redirectUri);
    }

    private static boolean isSet(String value) {
        return value != null && !value.isBlank();
    }
}
