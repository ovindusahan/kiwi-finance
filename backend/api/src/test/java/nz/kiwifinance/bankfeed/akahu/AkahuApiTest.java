package nz.kiwifinance.bankfeed.akahu;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.queryParam;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import nz.kiwifinance.bankfeed.BankFeedException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class AkahuApiTest {

    private static final AkahuCredentials CREDENTIALS = new AkahuCredentials("app_token_abc", "user_token_xyz");

    private MockRestServiceServer server;
    private AkahuApi api;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        api = new AkahuApi(builder, properties());
    }

    @Test
    void authenticatesWithUserAndAppTokens() {
        server.expect(requestTo("https://api.akahu.test/v1/me"))
                .andExpect(header("Authorization", "Bearer user_token_xyz"))
                .andExpect(header("X-Akahu-Id", "app_token_abc"))
                .andRespond(withSuccess("""
                        {"success": true, "item": {"_id": "user_1", "email": "aroha@example.nz", "access_granted_at": "2026-01-01T00:00:00Z"}}
                        """, MediaType.APPLICATION_JSON));

        assertThat(api.me(CREDENTIALS).id()).isEqualTo("user_1");
        server.verify();
    }

    @Test
    void readsAccounts() {
        server.expect(requestTo("https://api.akahu.test/v1/accounts"))
                .andRespond(withSuccess("""
                        {"success": true, "items": [{
                          "_id": "acc_1", "name": "Everyday", "status": "ACTIVE", "type": "CHECKING",
                          "formatted_account": "12-3456-0123456-00",
                          "connection": {"_id": "conn_1", "name": "ASB", "logo": "https://example/logo.png"},
                          "balance": {"current": 1234.56, "available": 1200.00, "currency": "NZD", "overdrawn": false},
                          "attributes": ["TRANSACTIONS", "PAYMENT_FROM"],
                          "refreshed": {"balance": "2026-09-30T20:00:00Z"},
                          "meta": {"holder": "A NGATA"}
                        }]}
                        """, MediaType.APPLICATION_JSON));

        var account = api.accounts(CREDENTIALS).getFirst();

        assertThat(account.balance().current()).isEqualByComparingTo(new BigDecimal("1234.56"));
        assertThat(account.connection().name()).isEqualTo("ASB");
        assertThat(account.refreshed().balance()).isEqualTo(Instant.parse("2026-09-30T20:00:00Z"));
    }

    @Test
    void followsTransactionCursorsToTheLastPage() {
        Instant start = Instant.parse("2026-09-01T00:00:00Z");
        Instant end = Instant.parse("2026-10-01T00:00:00Z");
        server.expect(requestTo(
                        org.hamcrest.Matchers.startsWith("https://api.akahu.test/v1/accounts/acc_1/transactions")))
                .andExpect(queryParam("start", "2026-09-01T00:00:00Z"))
                .andExpect(queryParam("end", "2026-10-01T00:00:00Z"))
                .andRespond(withSuccess(page("txn_1", "\"page_2\""), MediaType.APPLICATION_JSON));
        server.expect(requestTo(org.hamcrest.Matchers.containsString("cursor=page_2")))
                .andRespond(withSuccess(page("txn_2", "null"), MediaType.APPLICATION_JSON));

        var transactions = api.transactions(CREDENTIALS, "acc_1", start, end);

        assertThat(transactions).extracting(AkahuModels.Transaction::id).containsExactly("txn_1", "txn_2");
        server.verify();
    }

    @Test
    void exchangesAuthorisationCodes() {
        server.expect(requestTo("https://api.akahu.test/v1/token"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(jsonPath("$.grant_type").value("authorization_code"))
                .andExpect(jsonPath("$.code").value("code_123"))
                .andExpect(jsonPath("$.client_id").value("app_token_registered"))
                .andExpect(jsonPath("$.client_secret").value("secret"))
                .andExpect(jsonPath("$.redirect_uri").value("https://kiwi.example/connect/akahu/callback"))
                .andRespond(withSuccess("""
                        {"success": true, "access_token": "user_token_oauth", "token_type": "bearer", "scope": "ENDURING_CONSENT"}
                        """, MediaType.APPLICATION_JSON));

        assertThat(api.exchangeCode("code_123").accessToken()).isEqualTo("user_token_oauth");
    }

    @Test
    void classifiesFailures() {
        server.expect(requestTo("https://api.akahu.test/v1/me")).andRespond(withStatus(HttpStatus.UNAUTHORIZED));
        server.expect(requestTo("https://api.akahu.test/v1/me")).andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));
        server.expect(requestTo("https://api.akahu.test/v1/me")).andRespond(withStatus(HttpStatus.BAD_GATEWAY));

        assertThatThrownBy(() -> api.me(CREDENTIALS))
                .isInstanceOfSatisfying(
                        BankFeedException.class,
                        e -> assertThat(e.reason()).isEqualTo(BankFeedException.Reason.UNAUTHORISED));
        assertThatThrownBy(() -> api.me(CREDENTIALS))
                .isInstanceOfSatisfying(
                        BankFeedException.class,
                        e -> assertThat(e.reason()).isEqualTo(BankFeedException.Reason.RATE_LIMITED));
        assertThatThrownBy(() -> api.me(CREDENTIALS))
                .isInstanceOfSatisfying(
                        BankFeedException.class,
                        e -> assertThat(e.reason()).isEqualTo(BankFeedException.Reason.UNAVAILABLE));
    }

    @Test
    void revokesTokens() {
        server.expect(requestTo("https://api.akahu.test/v1/token"))
                .andExpect(method(HttpMethod.DELETE))
                .andExpect(header("Authorization", "Bearer user_token_xyz"))
                .andRespond(withSuccess("{\"success\": true}", MediaType.APPLICATION_JSON));

        api.revoke(CREDENTIALS);
        server.verify();
    }

    private static String page(String id, String next) {
        return """
                {"success": true, "items": [{
                  "_id": "%s", "_account": "acc_1", "_connection": "conn_1", "date": "2026-09-14T12:00:00.000Z",
                  "description": "COUNTDOWN PONSONBY", "amount": -84.5, "balance": 1000, "type": "EFTPOS",
                  "merchant": {"_id": "m_1", "name": "Countdown"},
                  "category": {"_id": "c_1", "name": "Supermarkets and grocery stores", "groups": {"personal_finance": {"_id": "g_1", "name": "Food"}}}
                }], "cursor": {"next": %s}}
                """.formatted(id, next);
    }

    static AkahuProperties properties() {
        return new AkahuProperties(
                "https://api.akahu.test/v1",
                "https://oauth.akahu.test",
                "https://my.akahu.test",
                "app_token_registered",
                "secret",
                "https://kiwi.example/connect/akahu/callback",
                Duration.ofSeconds(5),
                Duration.ofSeconds(30),
                365,
                7,
                Duration.ofMinutes(15),
                new AkahuProperties.ScheduledSync(false, "0 0 * * * *"));
    }
}
