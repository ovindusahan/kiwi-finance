package nz.kiwifinance.transaction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import nz.kiwifinance.support.IntegrationTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import tools.jackson.databind.JsonNode;

class TransactionIntegrationTest extends IntegrationTestBase {

    @Autowired
    private TransactionService transactionService;

    @Test
    void createsTransactionsAndAppliesRules() throws Exception {
        TestUser user = register("Ana");
        UUID account = account(user);
        String groceries = categoryId("groceries");
        postAs(
                        user,
                        "/api/v1/categorisation-rules",
                        Map.of(
                                "matchType",
                                "CONTAINS",
                                "pattern",
                                "countdown",
                                "categoryId",
                                groceries,
                                "priority",
                                10))
                .andExpect(status().isCreated());

        postAs(user, "/api/v1/transactions", transaction(account, "2026-09-10", -8_450, "COUNTDOWN PONSONBY"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.category.slug").value("groceries"))
                .andExpect(jsonPath("$.categorySource").value("RULE"))
                .andExpect(jsonPath("$.accountName").value("Everyday"))
                .andExpect(jsonPath("$.amount.cents").value(-8_450));
    }

    @Test
    void patchLeavesOmittedFieldsAlone() throws Exception {
        TestUser user = register("Ana");
        UUID account = account(user);
        Map<String, Object> body = transaction(account, "2026-09-10", -1_500, "Coffee");
        body.put("categoryId", categoryId("eating-out"));
        body.put("notes", "with Mere");
        UUID id = idOf(postAs(user, "/api/v1/transactions", body));

        patchAs(user, "/api/v1/transactions/" + id, Map.of("transfer", true))
                .andExpect(jsonPath("$.category.slug").value("eating-out"))
                .andExpect(jsonPath("$.notes").value("with Mere"))
                .andExpect(jsonPath("$.transfer").value(true));
        patchAs(user, "/api/v1/transactions/" + id, Map.of("notes", ""))
                .andExpect(jsonPath("$.notes").doesNotExist())
                .andExpect(jsonPath("$.category.slug").value("eating-out"));
    }

    @Test
    void setsAndClearsCategoriesExplicitly() throws Exception {
        TestUser user = register("Ana");
        UUID account = account(user);
        UUID first = idOf(postAs(user, "/api/v1/transactions", transaction(account, "2026-09-10", -1_500, "Coffee")));
        UUID second = idOf(postAs(user, "/api/v1/transactions", transaction(account, "2026-09-11", -1_800, "Brunch")));

        postAs(
                        user,
                        "/api/v1/transactions/categorise",
                        Map.of("transactionIds", List.of(first, second), "categoryId", categoryId("eating-out")))
                .andExpect(jsonPath("$.updated").value(2));
        getAs(user, "/api/v1/transactions/" + second)
                .andExpect(jsonPath("$.category.slug").value("eating-out"))
                .andExpect(jsonPath("$.categorySource").value("USER"));

        Map<String, Object> clear = new HashMap<>();
        clear.put("categoryId", null);
        putAs(user, "/api/v1/transactions/" + first + "/category", clear)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.category").doesNotExist())
                .andExpect(jsonPath("$.categorySource").doesNotExist());
    }

    @Test
    void paginatesNewestFirstWithoutDuplicates() throws Exception {
        TestUser user = register("Ana");
        UUID account = account(user);
        for (int day = 1; day <= 7; day++) {
            postAs(
                    user,
                    "/api/v1/transactions",
                    transaction(account, "2026-09-0" + day, -100 * day, "Purchase " + day));
            postAs(user, "/api/v1/transactions", transaction(account, "2026-09-0" + day, -100 * day, "Second " + day));
        }

        Set<String> seen = new HashSet<>();
        String cursor = null;
        String previousDate = "9999-12-31";
        int pages = 0;
        do {
            JsonNode page =
                    bodyOf(getAs(user, "/api/v1/transactions?limit=4" + (cursor == null ? "" : "&cursor=" + cursor))
                            .andExpect(status().isOk()));
            for (JsonNode item : page.get("items")) {
                assertThat(seen.add(item.get("id").asString())).isTrue();
                assertThat(item.get("postedOn").asString()).isLessThanOrEqualTo(previousDate);
                previousDate = item.get("postedOn").asString();
            }
            cursor = page.get("nextCursor").isNull()
                    ? null
                    : page.get("nextCursor").asString();
            pages++;
        } while (cursor != null);

        assertThat(seen).hasSize(14);
        assertThat(pages).isEqualTo(4);
    }

    @Test
    void filtersBySearchDirectionAndDate() throws Exception {
        TestUser user = register("Ana");
        UUID account = account(user);
        postAs(user, "/api/v1/transactions", transaction(account, "2026-08-31", 500_000, "SALARY ACME LTD"));
        postAs(user, "/api/v1/transactions", transaction(account, "2026-09-02", -4_000, "Z ENERGY"));
        postAs(user, "/api/v1/transactions", transaction(account, "2026-09-03", -2_000, "Spark 100%_fibre"));

        getAs(user, "/api/v1/transactions?direction=IN")
                .andExpect(jsonPath("$.items.length()").value(1));
        getAs(user, "/api/v1/transactions?search=energy")
                .andExpect(jsonPath("$.items[0].description").value("Z ENERGY"));
        perform(get("/api/v1/transactions").param("search", "100%_"), user.token(), null)
                .andExpect(jsonPath("$.items.length()").value(1));
        perform(get("/api/v1/transactions").param("search", "50%"), user.token(), null)
                .andExpect(jsonPath("$.items.length()").value(0));
        getAs(user, "/api/v1/transactions?from=2026-09-01&to=2026-09-02")
                .andExpect(jsonPath("$.items.length()").value(1));
        getAs(user, "/api/v1/transactions?uncategorised=true")
                .andExpect(jsonPath("$.items.length()").value(3));
        getAs(user, "/api/v1/transactions?cursor=garbage")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("invalid_cursor"));
    }

    @Test
    void importsIdempotentlyAndKeepsUserCategories() throws Exception {
        TestUser user = register("Ana");
        UUID account = account(user);
        UUID fuel = UUID.fromString(categoryId("fuel"));
        var first = List.of(
                new ExternalTransaction("ext-1", LocalDate.of(2026, 9, 1), -6_000, "Z ENERGY", "Z Energy", fuel, false),
                new ExternalTransaction("ext-2", LocalDate.of(2026, 9, 2), -1_000, "UNKNOWN", null, null, false));

        assertThat(transactionService.importTransactions(user.id(), account, TransactionSource.BANK_FEED, first))
                .isEqualTo(new ImportResult(2, 0, 0));
        assertThat(transactionService.importTransactions(user.id(), account, TransactionSource.BANK_FEED, first))
                .isEqualTo(new ImportResult(0, 0, 2));

        JsonNode page = bodyOf(getAs(user, "/api/v1/transactions?search=unknown"));
        String id = page.get("items").get(0).get("id").asString();
        putAs(user, "/api/v1/transactions/" + id + "/category", Map.of("categoryId", categoryId("shopping")));
        patchAs(user, "/api/v1/transactions/" + id, Map.of("amountCents", 5))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("transaction_managed_by_bank_feed"));

        var second = List.of(
                new ExternalTransaction("ext-2", LocalDate.of(2026, 9, 2), -1_000, "UNKNOWN", null, fuel, false));
        transactionService.importTransactions(user.id(), account, TransactionSource.BANK_FEED, second);
        getAs(user, "/api/v1/transactions/" + id)
                .andExpect(jsonPath("$.category.slug").value("shopping"))
                .andExpect(jsonPath("$.categorySource").value("USER"));
        deleteAs(user, "/api/v1/transactions/" + id).andExpect(status().isConflict());
    }

    @Test
    void recategorisesWhenRulesChange() throws Exception {
        TestUser user = register("Ana");
        UUID account = account(user);
        postAs(user, "/api/v1/transactions", transaction(account, "2026-09-01", -2_500, "NETFLIX.COM"));
        postAs(
                user,
                "/api/v1/categorisation-rules",
                Map.of(
                        "matchType",
                        "STARTS_WITH",
                        "pattern",
                        "netflix",
                        "categoryId",
                        categoryId("subscriptions"),
                        "priority",
                        0));

        postAs(user, "/api/v1/transactions/recategorise", null)
                .andExpect(jsonPath("$.updated").value(1));
        getAs(user, "/api/v1/transactions")
                .andExpect(jsonPath("$.items[0].category.slug").value("subscriptions"));
    }

    @Test
    void deletingACustomCategoryUncategorisesItsTransactions() throws Exception {
        TestUser user = register("Ana");
        UUID account = account(user);
        UUID pets = idOf(postAs(
                user,
                "/api/v1/categories",
                Map.of("name", "Pets", "group", "ESSENTIALS", "icon", "paw", "colour", "#F59E0B")));
        Map<String, Object> body = transaction(account, "2026-09-01", -9_000, "Vet");
        body.put("categoryId", pets.toString());
        UUID id = idOf(postAs(user, "/api/v1/transactions", body));

        deleteAs(user, "/api/v1/categories/" + pets).andExpect(status().isNoContent());
        getAs(user, "/api/v1/transactions/" + id)
                .andExpect(jsonPath("$.category").doesNotExist());
    }

    @Test
    void hidesOtherPeoplesTransactions() throws Exception {
        TestUser owner = register("Owner");
        TestUser other = register("Other");
        UUID account = account(owner);
        UUID id = idOf(postAs(owner, "/api/v1/transactions", transaction(account, "2026-09-01", -100, "Secret")));

        getAs(other, "/api/v1/transactions/" + id).andExpect(status().isNotFound());
        patchAs(other, "/api/v1/transactions/" + id, Map.of("transfer", true)).andExpect(status().isNotFound());
        getAs(other, "/api/v1/transactions")
                .andExpect(jsonPath("$.items.length()").value(0));
        postAs(other, "/api/v1/transactions", transaction(account, "2026-09-01", -100, "Sneaky"))
                .andExpect(status().isNotFound());
    }

    private UUID account(TestUser user) throws Exception {
        return idOf(postAs(user, "/api/v1/accounts", Map.of("name", "Everyday", "type", "EVERYDAY")));
    }

    private String categoryId(String slug) {
        return jdbc.queryForObject("select id::text from categories where slug = ?", String.class, slug);
    }

    private static Map<String, Object> transaction(UUID account, String date, long cents, String description) {
        Map<String, Object> body = new HashMap<>();
        body.put("accountId", account.toString());
        body.put("postedOn", date);
        body.put("amountCents", cents);
        body.put("description", description);
        return body;
    }
}
