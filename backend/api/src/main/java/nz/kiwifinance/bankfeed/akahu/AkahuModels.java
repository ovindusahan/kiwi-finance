package nz.kiwifinance.bankfeed.akahu;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Akahu API response shapes. Only the fields Kiwi Finance uses are mapped.
 */
final class AkahuModels {

    private AkahuModels() {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record ItemResponse<T>(boolean success, T item) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record ItemsResponse<T>(boolean success, List<T> items, Cursor cursor) {

        String nextCursor() {
            return cursor == null ? null : cursor.next();
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Cursor(String next) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record User(@JsonProperty("_id") String id, String email) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Account(
            @JsonProperty("_id") String id,
            String name,
            String status,
            String type,
            @JsonProperty("formatted_account") String formattedAccount,
            Connection connection,
            Balance balance,
            List<String> attributes,
            Refreshed refreshed) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Connection(@JsonProperty("_id") String id, String name, String logo) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Balance(BigDecimal current, BigDecimal available, String currency, Boolean overdrawn) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Refreshed(Instant balance, Instant transactions, Instant meta) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Transaction(
            @JsonProperty("_id") String id,
            @JsonProperty("_account") String account,
            Instant date,
            String description,
            BigDecimal amount,
            String type,
            Merchant merchant,
            Category category) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Merchant(@JsonProperty("_id") String id, String name) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Category(@JsonProperty("_id") String id, String name, Groups groups) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Groups(@JsonProperty("personal_finance") Group personalFinance) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Group(@JsonProperty("_id") String id, String name) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Token(
            boolean success,
            @JsonProperty("access_token") String accessToken,
            @JsonProperty("token_type") String tokenType,
            String scope) {}

    record TokenExchange(
            @JsonProperty("grant_type") String grantType,
            String code,
            @JsonProperty("redirect_uri") String redirectUri,
            @JsonProperty("client_id") String clientId,
            @JsonProperty("client_secret") String clientSecret) {}
}
