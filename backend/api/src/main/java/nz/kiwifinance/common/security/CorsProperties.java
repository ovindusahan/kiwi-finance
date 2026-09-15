package nz.kiwifinance.common.security;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Origins allowed to call the API from a browser. The Next.js web app calls the API from its
 * server, so production deployments normally leave this empty.
 */
@ConfigurationProperties("kiwi.cors")
public record CorsProperties(List<String> allowedOrigins) {

    public CorsProperties {
        allowedOrigins = allowedOrigins == null ? List.of() : List.copyOf(allowedOrigins);
    }
}
