package nz.kiwifinance.privacy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;
import nz.kiwifinance.support.IntegrationTestBase;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

class DataExportIntegrationTest extends IntegrationTestBase {

    @Test
    void exportsThePersonsOwnDataWithoutSecrets() throws Exception {
        TestUser user = register("Ana");
        TestUser other = register("Other");
        String account = idOf(postAs(user, "/api/v1/accounts", Map.of("name", "Everyday", "type", "EVERYDAY")))
                .toString();
        postAs(
                user,
                "/api/v1/transactions",
                Map.of(
                        "accountId",
                        account,
                        "postedOn",
                        "2026-09-10",
                        "amountCents",
                        -8_450,
                        "description",
                        "Countdown"));
        postAs(other, "/api/v1/accounts", Map.of("name", "Not mine", "type", "SAVINGS"));

        JsonNode export = bodyOf(getAs(user, "/api/v1/auth/me/export")
                .andExpect(status().isOk())
                .andExpect(header().string(
                                "Content-Disposition",
                                org.hamcrest.Matchers.startsWith("attachment; filename=\"kiwi-finance-data-")))
                .andExpect(jsonPath("$.tables.user[0].email").value(user.email()))
                .andExpect(jsonPath("$.tables.accounts.length()").value(1))
                .andExpect(jsonPath("$.tables.accounts[0].name").value("Everyday"))
                .andExpect(jsonPath("$.tables.transactions[0].amount_cents").value(-8_450))
                .andExpect(jsonPath("$.tables.transactions[0].posted_on").value("2026-09-10")));

        String text = export.toString();
        assertThat(text).doesNotContain("password_hash", "token_hash", "credentials", "Not mine");
        assertThat(export.get("tables").propertyNames())
                .contains("profile", "goals", "budgets", "bankConnections", "purchasePlans");
    }

    @Test
    void needsAnAccessToken() throws Exception {
        perform(
                        org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(
                                "/api/v1/auth/me/export"),
                        null,
                        null)
                .andExpect(status().isUnauthorized());
    }
}
