package nz.kiwifinance.account;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;
import java.util.UUID;
import nz.kiwifinance.support.IntegrationTestBase;
import org.junit.jupiter.api.Test;

class AccountIntegrationTest extends IntegrationTestBase {

    @Test
    void createsAccountsWithLiquidityFromTheType() throws Exception {
        TestUser user = register("Ana");

        postAs(user, "/api/v1/accounts", Map.of("name", "Everyday", "type", "EVERYDAY", "currentBalanceCents", 125_050))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.liquid").value(true))
                .andExpect(jsonPath("$.balance.cents").value(125_050))
                .andExpect(jsonPath("$.balance.currency").value("NZD"))
                .andExpect(jsonPath("$.managedBy").value("USER"));
        postAs(user, "/api/v1/accounts", Map.of("name", "KiwiSaver", "type", "KIWISAVER"))
                .andExpect(jsonPath("$.liquid").value(false));

        getAs(user, "/api/v1/accounts").andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    void updatesAndArchivesAccounts() throws Exception {
        TestUser user = register("Ana");
        UUID id = idOf(
                postAs(user, "/api/v1/accounts", Map.of("name", "Savings", "type", "SAVINGS", "institution", "ASB")));

        patchAs(user, "/api/v1/accounts/" + id, Map.of("name", "Rainy day", "institution", ""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Rainy day"))
                .andExpect(jsonPath("$.institution").doesNotExist());

        deleteAs(user, "/api/v1/accounts/" + id).andExpect(status().isNoContent());
        getAs(user, "/api/v1/accounts").andExpect(jsonPath("$.length()").value(0));
        getAs(user, "/api/v1/accounts?includeArchived=true")
                .andExpect(jsonPath("$[0].archived").value(true));
    }

    @Test
    void hidesOtherPeoplesAccounts() throws Exception {
        TestUser owner = register("Owner");
        TestUser other = register("Other");
        UUID id = idOf(postAs(owner, "/api/v1/accounts", Map.of("name", "Private", "type", "SAVINGS")));

        getAs(other, "/api/v1/accounts/" + id)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("not_found"));
        patchAs(other, "/api/v1/accounts/" + id, Map.of("name", "Mine now")).andExpect(status().isNotFound());
        deleteAs(other, "/api/v1/accounts/" + id).andExpect(status().isNotFound());
        getAs(other, "/api/v1/accounts").andExpect(jsonPath("$.length()").value(0));
    }
}
