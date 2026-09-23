package nz.kiwifinance.preferences;

import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import nz.kiwifinance.support.IntegrationTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PreferencesIntegrationTest extends IntegrationTestBase {

    private TestUser user;

    @BeforeEach
    void register() {
        user = register("Wiremu");
    }

    @Test
    void savesTheHomeLayoutToTheAccount() throws Exception {
        getAs(user, "/api/v1/preferences")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.homeLayout").value(nullValue()))
                .andExpect(jsonPath("$.emergencyFundReminders").value(true));

        List<Map<String, String>> widgets = List.of(
                Map.of("id", "goals", "size", "FULL"),
                Map.of("id", "accounts", "size", "LARGE"),
                Map.of("id", "score", "size", "SMALL"));
        putAs(user, "/api/v1/preferences/home-layout", Map.of("widgets", widgets))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.homeLayout.length()").value(3));
        getAs(user, "/api/v1/preferences")
                .andExpect(jsonPath("$.homeLayout[0].id").value("goals"))
                .andExpect(jsonPath("$.homeLayout[1].size").value("LARGE"));

        Map<String, Object> reset = new HashMap<>();
        reset.put("widgets", null);
        putAs(user, "/api/v1/preferences/home-layout", reset)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.homeLayout").value(nullValue()));
    }

    @Test
    void rejectsLayoutsThatDoNotMakeSense() throws Exception {
        putAs(
                        user,
                        "/api/v1/preferences/home-layout",
                        Map.of(
                                "widgets",
                                List.of(Map.of("id", "goals", "size", "FULL"), Map.of("id", "goals", "size", "SMALL"))))
                .andExpect(status().isBadRequest());
        putAs(
                        user,
                        "/api/v1/preferences/home-layout",
                        Map.of("widgets", List.of(Map.of("id", "<script>", "size", "FULL"))))
                .andExpect(status().isBadRequest());
        putAs(
                        user,
                        "/api/v1/preferences/home-layout",
                        Map.of("widgets", List.of(Map.of("id", "goals", "size", "HUGE"))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void remindsAboutTheEmergencyFundUntilAnAccountIsChosen() throws Exception {
        UUID savings = idOf(postAs(user, "/api/v1/accounts", Map.of("name", "Savings", "type", "SAVINGS")));
        getAs(user, "/api/v1/emergency-fund")
                .andExpect(jsonPath("$.remind").value(true))
                .andExpect(jsonPath("$.account").value(nullValue()))
                .andExpect(jsonPath("$.suggestedAccount.id").value(savings.toString()));

        postAs(user, "/api/v1/preferences/emergency-fund-reminder/snooze", Map.of())
                .andExpect(jsonPath("$.emergencyFundReminderSnoozedUntil").isString());
        getAs(user, "/api/v1/emergency-fund").andExpect(jsonPath("$.remind").value(false));
        getAs(user, "/api/v1/dashboard")
                .andExpect(jsonPath("$.emergencyFundReminder").value(false));

        patchAs(user, "/api/v1/preferences", Map.of("emergencyFundReminders", false))
                .andExpect(jsonPath("$.emergencyFundReminders").value(false));
        getAs(user, "/api/v1/emergency-fund").andExpect(jsonPath("$.remind").value(false));

        patchAs(user, "/api/v1/preferences", Map.of("emergencyFundReminders", true))
                .andExpect(jsonPath("$.emergencyFundReminderSnoozedUntil").value(nullValue()));
        getAs(user, "/api/v1/dashboard")
                .andExpect(jsonPath("$.emergencyFundReminder").value(true));

        putAs(user, "/api/v1/emergency-fund/account", Map.of("accountId", savings.toString()))
                .andExpect(jsonPath("$.account.name").value("Savings"))
                .andExpect(jsonPath("$.remind").value(false));
        getAs(user, "/api/v1/dashboard")
                .andExpect(jsonPath("$.emergencyFundReminder").value(false));
    }

    @Test
    void keepsOneEmergencyFundAccount() throws Exception {
        UUID first = idOf(postAs(user, "/api/v1/accounts", Map.of("name", "First", "type", "SAVINGS")));
        UUID second = idOf(postAs(user, "/api/v1/accounts", Map.of("name", "Second", "type", "SAVINGS")));
        putAs(user, "/api/v1/emergency-fund/account", Map.of("accountId", first.toString()))
                .andExpect(status().isOk());
        putAs(user, "/api/v1/emergency-fund/account", Map.of("accountId", second.toString()))
                .andExpect(jsonPath("$.account.name").value("Second"));
        getAs(user, "/api/v1/accounts")
                .andExpect(
                        jsonPath("$[?(@.includeInEmergencyFund == true)].name").value("Second"));

        deleteAs(user, "/api/v1/accounts/" + second).andExpect(status().isNoContent());
        getAs(user, "/api/v1/emergency-fund").andExpect(jsonPath("$.account").value(nullValue()));

        Map<String, Object> clear = new HashMap<>();
        clear.put("accountId", null);
        putAs(user, "/api/v1/emergency-fund/account", Map.of("accountId", first.toString()));
        putAs(user, "/api/v1/emergency-fund/account", clear)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.account").value(nullValue()));
    }
}
