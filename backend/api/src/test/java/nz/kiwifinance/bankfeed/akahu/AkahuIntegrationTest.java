package nz.kiwifinance.bankfeed.akahu;

import static nz.kiwifinance.bankfeed.akahu.AkahuFixtures.APP_TOKEN;
import static nz.kiwifinance.bankfeed.akahu.AkahuFixtures.USER_TOKEN;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import nz.kiwifinance.bankfeed.BankFeedException;
import nz.kiwifinance.support.IntegrationTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import tools.jackson.databind.JsonNode;

class AkahuIntegrationTest extends IntegrationTestBase {

    private static final Map<String, String> TOKENS = Map.of("appToken", APP_TOKEN, "userToken", USER_TOKEN);

    @MockitoBean
    private AkahuApi akahuApi;

    @BeforeEach
    void stubAkahu() {
        when(akahuApi.me(any())).thenReturn(AkahuFixtures.user("akahu_user_1"));
        when(akahuApi.accounts(any())).thenReturn(AkahuFixtures.accounts("1520.75"));
        when(akahuApi.transactions(any(), eq("acc_everyday"), any(), any())).thenReturn(AkahuFixtures.transactions());
    }

    @Test
    void guidesANewPersonThroughPersonalAppSetup() throws Exception {
        TestUser user = register("Aroha");

        getAs(user, "/api/v1/bank-feeds/akahu/setup-guide")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("NOT_CONNECTED"))
                .andExpect(jsonPath("$.recommendedMethod").value("PERSONAL_APP"))
                .andExpect(jsonPath("$.methods[0].method").value("PERSONAL_APP"))
                .andExpect(jsonPath("$.methods[0].steps[0].status").value("CURRENT"))
                .andExpect(jsonPath("$.methods[0].steps[0].action.type").value("OPEN_LINK"))
                .andExpect(jsonPath("$.methods[0].steps[3].action.type").value("ENTER_TOKENS"))
                .andExpect(jsonPath("$.methods[1].method").value("OAUTH"))
                .andExpect(jsonPath("$.methods[1].available").value(false))
                .andExpect(jsonPath("$.security.length()").value(4));
    }

    @Test
    void validatesTokenFormatsBeforeCallingAkahu() throws Exception {
        TestUser user = register("Aroha");

        postAs(
                        user,
                        "/api/v1/bank-feeds/akahu/personal-connections",
                        Map.of("appToken", "user_token_swapped", "userToken", "nope"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.length()").value(2));
    }

    @Test
    void reportsTokensAkahuRejects() throws Exception {
        TestUser user = register("Aroha");
        when(akahuApi.me(any())).thenThrow(new BankFeedException(BankFeedException.Reason.UNAUTHORISED, "nope"));

        postAs(user, "/api/v1/bank-feeds/akahu/personal-connections", TOKENS)
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("akahu_tokens_rejected"));
        assertThat(jdbc.queryForObject("select count(*) from bank_connections", Integer.class))
                .isZero();
    }

    @Test
    void connectsChoosesAccountsAndSyncs() throws Exception {
        TestUser user = register("Aroha");

        JsonNode connection = bodyOf(postAs(user, "/api/v1/bank-feeds/akahu/personal-connections", TOKENS)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.method").value("PERSONAL_APP"))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.accounts.length()").value(2)));
        String connectionId = connection.get("id").asString();
        JsonNode everyday = findAccount(connection, "Everyday");
        assertThat(everyday.get("maskedNumber").asString()).isEqualTo("**-****-*****56-00");
        assertThat(everyday.get("syncEnabled").asBoolean()).isFalse();

        byte[] stored = jdbc.queryForObject("select credentials from bank_connections", byte[].class);
        assertThat(new String(stored, StandardCharsets.ISO_8859_1))
                .doesNotContain(USER_TOKEN)
                .doesNotContain(APP_TOKEN);

        getAs(user, "/api/v1/bank-feeds/akahu/setup-guide")
                .andExpect(jsonPath("$.state").value("CHOOSE_ACCOUNTS"))
                .andExpect(jsonPath("$.methods[0].steps[3].status").value("DONE"))
                .andExpect(jsonPath("$.methods[0].steps[4].status").value("CURRENT"))
                .andExpect(jsonPath("$.methods[0].steps[4].action.type").value("CHOOSE_ACCOUNTS"));

        JsonNode enabled = bodyOf(patchAs(user, feedAccountPath(connectionId, everyday), Map.of("syncEnabled", true))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.syncEnabled").value(true)));
        String accountId = enabled.get("linkedAccountId").asString();

        awaitSyncFinished(user, connectionId);

        getAs(user, "/api/v1/bank-feeds/connections/" + connectionId + "/syncs")
                .andExpect(jsonPath("$[0].status").value("SUCCEEDED"))
                .andExpect(jsonPath("$[0].trigger").value("INITIAL"))
                .andExpect(jsonPath("$[0].transactionsCreated").value(4));
        getAs(user, "/api/v1/accounts/" + accountId)
                .andExpect(jsonPath("$.managedBy").value("BANK_FEED"))
                .andExpect(jsonPath("$.balance.cents").value(152_075))
                .andExpect(jsonPath("$.institution").value("ASB"));
        getAs(user, "/api/v1/transactions?accountId=" + accountId)
                .andExpect(jsonPath("$.items.length()").value(4))
                .andExpect(jsonPath("$.items[0].postedOn").value("2026-09-30"))
                .andExpect(jsonPath("$.items[0].category.slug").value("groceries"))
                .andExpect(jsonPath("$.items[0].categorySource").value("PROVIDER"))
                .andExpect(jsonPath("$.items[0].source").value("BANK_FEED"))
                .andExpect(jsonPath("$.items[3].transfer").value(true));
        getAs(user, "/api/v1/bank-feeds/akahu/setup-guide")
                .andExpect(jsonPath("$.state").value("CONNECTED"))
                .andExpect(jsonPath("$.connection.accountsSyncing").value(1));

        postAs(user, "/api/v1/bank-feeds/connections/" + connectionId + "/syncs", null)
                .andExpect(status().isAccepted());
        awaitSyncFinished(user, connectionId);
        getAs(user, "/api/v1/bank-feeds/connections/" + connectionId + "/syncs")
                .andExpect(jsonPath("$[0].trigger").value("MANUAL"))
                .andExpect(jsonPath("$[0].transactionsCreated").value(0));
        assertThat(jdbc.queryForObject("select count(*) from transactions", Integer.class))
                .isEqualTo(4);
    }

    @Test
    void refusesASecondConnectionAndOverlappingSyncs() throws Exception {
        TestUser user = register("Aroha");
        String connectionId = connect(user);

        postAs(user, "/api/v1/bank-feeds/akahu/personal-connections", TOKENS)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("bank_connection_exists"));

        jdbc.update("update bank_connections set sync_locked_until = now() + interval '5 minutes'");
        postAs(user, "/api/v1/bank-feeds/connections/" + connectionId + "/syncs", null)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("sync_in_progress"));
    }

    @Test
    void asksForNewTokensWhenAkahuRevokesAccess() throws Exception {
        TestUser user = register("Aroha");
        String connectionId = connect(user);
        when(akahuApi.accounts(any()))
                .thenThrow(new BankFeedException(BankFeedException.Reason.UNAUTHORISED, "revoked"));

        postAs(user, "/api/v1/bank-feeds/connections/" + connectionId + "/syncs", null)
                .andExpect(status().isAccepted());
        awaitSyncFinished(user, connectionId);

        getAs(user, "/api/v1/bank-feeds/connections/" + connectionId)
                .andExpect(jsonPath("$.status").value("REAUTH_REQUIRED"))
                .andExpect(jsonPath("$.latestSync.errorCode").value("reauthorisation_required"));
        getAs(user, "/api/v1/bank-feeds/akahu/setup-guide")
                .andExpect(jsonPath("$.state").value("NEEDS_ATTENTION"))
                .andExpect(jsonPath("$.methods[0].steps[3].status").value("ATTENTION"))
                .andExpect(jsonPath("$.methods[0].steps[3].action.type").value("UPDATE_TOKENS"));
        postAs(user, "/api/v1/bank-feeds/connections/" + connectionId + "/syncs", null)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("bank_connection_not_active"));

        doReturn(AkahuFixtures.accounts("10.00")).when(akahuApi).accounts(any());
        putAs(user, "/api/v1/bank-feeds/akahu/connections/" + connectionId + "/credentials", TOKENS)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(connectionId))
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    @Test
    void rejectsTokensForADifferentAkahuAccount() throws Exception {
        TestUser user = register("Aroha");
        String connectionId = connect(user);
        when(akahuApi.me(any())).thenReturn(AkahuFixtures.user("someone_else"));

        putAs(user, "/api/v1/bank-feeds/akahu/connections/" + connectionId + "/credentials", TOKENS)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("bank_connection_different_user"));
    }

    @Test
    void linksAFeedToAnAccountTrackedByHand() throws Exception {
        TestUser user = register("Aroha");
        UUID manual = idOf(postAs(
                user,
                "/api/v1/accounts",
                Map.of("name", "ASB everyday", "type", "EVERYDAY", "currentBalanceCents", 100)));
        String connectionId = connect(user);
        JsonNode everyday =
                findAccount(bodyOf(getAs(user, "/api/v1/bank-feeds/connections/" + connectionId)), "Everyday");

        patchAs(
                        user,
                        feedAccountPath(connectionId, everyday),
                        Map.of("syncEnabled", true, "linkAccountId", manual.toString()))
                .andExpect(jsonPath("$.linkedAccountId").value(manual.toString()));
        awaitSyncFinished(user, connectionId);

        getAs(user, "/api/v1/accounts/" + manual)
                .andExpect(jsonPath("$.managedBy").value("BANK_FEED"))
                .andExpect(jsonPath("$.balance.cents").value(152_075));
        patchAs(user, "/api/v1/accounts/" + manual, Map.of("currentBalanceCents", 5))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("account_managed_by_bank_feed"));
    }

    @Test
    void disconnectsAndReconnectsWithoutLosingHistory() throws Exception {
        TestUser user = register("Aroha");
        String connectionId = connect(user);
        JsonNode everyday =
                findAccount(bodyOf(getAs(user, "/api/v1/bank-feeds/connections/" + connectionId)), "Everyday");
        String accountId = bodyOf(patchAs(user, feedAccountPath(connectionId, everyday), Map.of("syncEnabled", true)))
                .get("linkedAccountId")
                .asString();
        awaitSyncFinished(user, connectionId);

        deleteAs(user, "/api/v1/bank-feeds/connections/" + connectionId).andExpect(status().isNoContent());

        getAs(user, "/api/v1/bank-feeds/connections")
                .andExpect(jsonPath("$.length()").value(0));
        getAs(user, "/api/v1/accounts/" + accountId)
                .andExpect(jsonPath("$.managedBy").value("USER"));
        assertThat(jdbc.queryForObject("select credentials is null from bank_connections", Boolean.class))
                .isTrue();
        assertThat(jdbc.queryForObject("select count(*) from transactions", Integer.class))
                .isEqualTo(4);

        String reconnected = connect(user);
        assertThat(reconnected).isEqualTo(connectionId);
        patchAs(user, feedAccountPath(connectionId, everyday), Map.of("syncEnabled", true))
                .andExpect(jsonPath("$.linkedAccountId").value(accountId));
    }

    @Test
    void hidesOtherPeoplesConnections() throws Exception {
        TestUser owner = register("Owner");
        TestUser other = register("Other");
        String connectionId = connect(owner);

        getAs(other, "/api/v1/bank-feeds/connections/" + connectionId).andExpect(status().isNotFound());
        postAs(other, "/api/v1/bank-feeds/connections/" + connectionId + "/syncs", null)
                .andExpect(status().isNotFound());
        deleteAs(other, "/api/v1/bank-feeds/connections/" + connectionId).andExpect(status().isNotFound());
        putAs(other, "/api/v1/bank-feeds/akahu/connections/" + connectionId + "/credentials", TOKENS)
                .andExpect(status().isNotFound());
        getAs(other, "/api/v1/bank-feeds/akahu/setup-guide")
                .andExpect(jsonPath("$.state").value("NOT_CONNECTED"));
    }

    @Test
    void explainsWhenOneClickConnectionsAreUnavailable() throws Exception {
        TestUser user = register("Aroha");

        postAs(user, "/api/v1/bank-feeds/akahu/oauth/authorisations", null)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("akahu_oauth_not_configured"));
    }

    private String connect(TestUser user) throws Exception {
        return bodyOf(postAs(user, "/api/v1/bank-feeds/akahu/personal-connections", TOKENS)
                        .andExpect(status().isCreated()))
                .get("id")
                .asString();
    }

    private void awaitSyncFinished(TestUser user, String connectionId) {
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            JsonNode runs = bodyOf(getAs(user, "/api/v1/bank-feeds/connections/" + connectionId + "/syncs"));
            assertThat(runs.size()).isPositive();
            assertThat(runs.get(0).get("status").asString()).isNotEqualTo("RUNNING");
            assertThat(jdbc.queryForObject(
                            "select sync_locked_until is null from bank_connections where id = ?::uuid",
                            Boolean.class,
                            connectionId))
                    .isTrue();
        });
    }

    private static JsonNode findAccount(JsonNode connection, String name) {
        for (JsonNode account : connection.get("accounts")) {
            if (account.get("name").asString().equals(name)) {
                return account;
            }
        }
        throw new AssertionError("No feed account named " + name);
    }

    private static String feedAccountPath(String connectionId, JsonNode feedAccount) {
        return "/api/v1/bank-feeds/connections/" + connectionId + "/accounts/"
                + feedAccount.get("id").asString();
    }
}
