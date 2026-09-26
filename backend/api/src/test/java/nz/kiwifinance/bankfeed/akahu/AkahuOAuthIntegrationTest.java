package nz.kiwifinance.bankfeed.akahu;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;
import nz.kiwifinance.support.IntegrationTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.util.UriComponents;
import org.springframework.web.util.UriComponentsBuilder;

@TestPropertySource(
        properties = {
            "kiwi.akahu.app-token=app_token_registered",
            "kiwi.akahu.app-secret=registered-secret",
            "kiwi.akahu.redirect-uri=https://kiwi.example/connect/akahu/callback"
        })
class AkahuOAuthIntegrationTest extends IntegrationTestBase {

    @MockitoBean
    private AkahuApi akahuApi;

    @BeforeEach
    void stubAkahu() {
        when(akahuApi.exchangeCode("code_from_akahu"))
                .thenReturn(new AkahuModels.Token(true, "user_token_oauth", "bearer", "ENDURING_CONSENT"));
        when(akahuApi.me(any())).thenReturn(AkahuFixtures.user("akahu_user_9"));
        when(akahuApi.accounts(any())).thenReturn(AkahuFixtures.accounts("50.00"));
    }

    @Test
    void connectsThroughAkahusConsentScreen() throws Exception {
        TestUser user = register("Aroha");
        getAs(user, "/api/v1/bank-feeds/akahu/setup-guide")
                .andExpect(jsonPath("$.recommendedMethod").value("OAUTH"))
                .andExpect(jsonPath("$.methods[0].method").value("OAUTH"))
                .andExpect(jsonPath("$.methods[0].available").value(true))
                .andExpect(jsonPath("$.methods[0].steps[0].action.type").value("START_OAUTH"));

        String url = bodyOf(postAs(user, "/api/v1/bank-feeds/akahu/oauth/authorisations", null)
                        .andExpect(status().isCreated()))
                .get("authorisationUrl")
                .asString();
        UriComponents uri = UriComponentsBuilder.fromUriString(url).build();
        assertThat(uri.getHost()).isEqualTo("oauth.akahu.nz");
        assertThat(uri.getQueryParams().getFirst("response_type")).isEqualTo("code");
        assertThat(uri.getQueryParams().getFirst("client_id")).isEqualTo("app_token_registered");
        assertThat(uri.getQueryParams().getFirst("scope")).isEqualTo("ENDURING_CONSENT");
        String state = uri.getQueryParams().getFirst("state");
        assertThat(state).hasSizeGreaterThan(30);

        postAs(user, "/api/v1/bank-feeds/akahu/oauth/callback", Map.of("code", "code_from_akahu", "state", "forged"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("oauth_state_invalid"));

        postAs(user, "/api/v1/bank-feeds/akahu/oauth/callback", Map.of("code", "code_from_akahu", "state", state))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.method").value("OAUTH"))
                .andExpect(jsonPath("$.accounts.length()").value(2));
        verify(akahuApi).me(eq(new AkahuCredentials("app_token_registered", "user_token_oauth")));

        postAs(user, "/api/v1/bank-feeds/akahu/oauth/callback", Map.of("code", "code_from_akahu", "state", state))
                .andExpect(status().isBadRequest());
        getAs(user, "/api/v1/bank-feeds/akahu/setup-guide")
                .andExpect(jsonPath("$.state").value("CHOOSE_ACCOUNTS"))
                .andExpect(jsonPath("$.methods[0].steps[0].status").value("DONE"))
                .andExpect(jsonPath("$.methods[0].steps[1].status").value("CURRENT"));
    }

    @Test
    void rejectsStatesIssuedToSomeoneElse() throws Exception {
        TestUser owner = register("Owner");
        TestUser attacker = register("Attacker");
        String url = bodyOf(postAs(owner, "/api/v1/bank-feeds/akahu/oauth/authorisations", null))
                .get("authorisationUrl")
                .asString();
        String state =
                UriComponentsBuilder.fromUriString(url).build().getQueryParams().getFirst("state");

        postAs(attacker, "/api/v1/bank-feeds/akahu/oauth/callback", Map.of("code", "code_from_akahu", "state", state))
                .andExpect(status().isBadRequest());
    }
}
