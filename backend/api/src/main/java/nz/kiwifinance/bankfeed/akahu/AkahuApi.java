package nz.kiwifinance.bankfeed.akahu;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import nz.kiwifinance.bankfeed.BankFeedException;
import nz.kiwifinance.bankfeed.BankFeedException.Reason;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

/**
 * Thin client for the Akahu REST API. Every call authenticates as one person with their user
 * token plus the app token, and failures are classified into {@link BankFeedException}s.
 */
public class AkahuApi {

    static final String APP_HEADER = "X-Akahu-Id";

    /**
     * Guards against a provider bug causing an endless cursor loop.
     */
    private static final int MAX_PAGES = 500;

    private static final ParameterizedTypeReference<AkahuModels.ItemResponse<AkahuModels.User>> USER =
            new ParameterizedTypeReference<>() {};
    private static final ParameterizedTypeReference<AkahuModels.ItemsResponse<AkahuModels.Account>> ACCOUNTS =
            new ParameterizedTypeReference<>() {};
    private static final ParameterizedTypeReference<AkahuModels.ItemsResponse<AkahuModels.Transaction>> TRANSACTIONS =
            new ParameterizedTypeReference<>() {};

    private final RestClient client;
    private final AkahuProperties properties;

    public AkahuApi(RestClient.Builder builder, AkahuProperties properties) {
        this.client = builder.baseUrl(properties.apiUrl()).build();
        this.properties = properties;
    }

    AkahuModels.User me(AkahuCredentials credentials) {
        return call(() -> client.get()
                        .uri("/me")
                        .headers(headers -> authenticate(headers, credentials))
                        .retrieve()
                        .body(USER))
                .item();
    }

    List<AkahuModels.Account> accounts(AkahuCredentials credentials) {
        var response = call(() -> client.get()
                .uri("/accounts")
                .headers(headers -> authenticate(headers, credentials))
                .retrieve()
                .body(ACCOUNTS));
        return response.items() == null ? List.of() : response.items();
    }

    /**
     * Settled transactions on one account after {@code start} (exclusive) up to {@code end}
     * (inclusive), following Akahu's cursor until the last page.
     */
    List<AkahuModels.Transaction> transactions(
            AkahuCredentials credentials, String accountId, Instant start, Instant end) {
        List<AkahuModels.Transaction> all = new ArrayList<>();
        String cursor = null;
        for (int page = 0; page < MAX_PAGES; page++) {
            String pageCursor = cursor;
            var response = call(() -> client.get()
                    .uri(uri -> {
                        uri.path("/accounts/{id}/transactions")
                                .queryParam("start", start.toString())
                                .queryParam("end", end.toString());
                        if (pageCursor != null) {
                            uri.queryParam("cursor", pageCursor);
                        }
                        return uri.build(accountId);
                    })
                    .headers(headers -> authenticate(headers, credentials))
                    .retrieve()
                    .body(TRANSACTIONS));
            if (response.items() != null) {
                all.addAll(response.items());
            }
            cursor = response.nextCursor();
            if (cursor == null) {
                return all;
            }
        }
        throw new BankFeedException(Reason.UNAVAILABLE, "Akahu returned too many pages of transactions");
    }

    AkahuModels.Token exchangeCode(String code) {
        var request = new AkahuModels.TokenExchange(
                "authorization_code", code, properties.redirectUri(), properties.appToken(), properties.appSecret());
        return call(() -> client.post()
                .uri("/token")
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .body(AkahuModels.Token.class));
    }

    void revoke(AkahuCredentials credentials) {
        call(() -> client.delete()
                .uri("/token")
                .headers(headers -> authenticate(headers, credentials))
                .retrieve()
                .toBodilessEntity());
    }

    private static void authenticate(HttpHeaders headers, AkahuCredentials credentials) {
        headers.setBearerAuth(credentials.userToken());
        headers.set(APP_HEADER, credentials.appToken());
    }

    private static <T> T call(Supplier<T> request) {
        try {
            T result = request.get();
            if (result == null) {
                throw new BankFeedException(Reason.UNAVAILABLE, "Akahu returned an empty response");
            }
            return result;
        } catch (RestClientResponseException e) {
            throw classify(e.getStatusCode(), e);
        } catch (RestClientException e) {
            throw new BankFeedException(Reason.UNAVAILABLE, "Could not reach Akahu", e);
        }
    }

    private static BankFeedException classify(HttpStatusCode status, RestClientResponseException cause) {
        if (status.value() == HttpStatus.UNAUTHORIZED.value() || status.value() == HttpStatus.FORBIDDEN.value()) {
            return new BankFeedException(Reason.UNAUTHORISED, "Akahu rejected the credentials", cause);
        }
        if (status.value() == HttpStatus.TOO_MANY_REQUESTS.value()) {
            return new BankFeedException(Reason.RATE_LIMITED, "Akahu rate limit reached", cause);
        }
        return new BankFeedException(Reason.UNAVAILABLE, "Akahu returned HTTP " + status.value(), cause);
    }
}
