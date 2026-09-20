package nz.kiwifinance.common.crypto;

import jakarta.validation.constraints.NotBlank;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Encryption keys for credentials stored at rest. Several keys can be configured so that old
 * records stay readable while new ones are written with {@code activeKeyId}.
 *
 * @param keys key ID to base64-encoded 256-bit AES key
 */
@Validated
@ConfigurationProperties("kiwi.crypto")
public record CryptoProperties(@NotBlank String activeKeyId, Map<String, String> keys) {

    public CryptoProperties {
        keys = keys == null ? Map.of() : Map.copyOf(keys);
        if (activeKeyId != null && !activeKeyId.isBlank() && !keys.containsKey(activeKeyId)) {
            throw new IllegalArgumentException("kiwi.crypto.keys must contain the active key '" + activeKeyId + "'");
        }
    }
}
