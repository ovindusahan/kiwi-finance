package nz.kiwifinance.category;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;
import java.util.UUID;
import nz.kiwifinance.support.IntegrationTestBase;
import org.junit.jupiter.api.Test;

class CategoryIntegrationTest extends IntegrationTestBase {

    @Test
    void listsNewZealandDefaultCategories() throws Exception {
        TestUser user = register("Ana");

        getAs(user, "/api/v1/categories")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(31))
                .andExpect(jsonPath("$[?(@.slug == 'power-gas')].name").value("Power & gas"))
                .andExpect(jsonPath("$[?(@.slug == 'kiwisaver')].group").value("SAVINGS"));
    }

    @Test
    void managesCustomCategories() throws Exception {
        TestUser user = register("Ana");
        TestUser other = register("Other");
        UUID id = idOf(postAs(
                        user,
                        "/api/v1/categories",
                        Map.of("name", "Pets", "group", "ESSENTIALS", "icon", "paw-print", "colour", "#F59E0B"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.custom").value(true)));

        postAs(
                        user,
                        "/api/v1/categories",
                        Map.of("name", "pets", "group", "LIFESTYLE", "icon", "dog", "colour", "#F59E0B"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("category_name_taken"));
        postAs(
                        user,
                        "/api/v1/categories",
                        Map.of("name", "Groceries", "group", "LIFESTYLE", "icon", "x", "colour", "#000000"))
                .andExpect(status().isConflict());
        getAs(other, "/api/v1/categories").andExpect(jsonPath("$.length()").value(31));

        patchAs(user, "/api/v1/categories/" + id, Map.of("name", "Pet care"))
                .andExpect(jsonPath("$.name").value("Pet care"));
        deleteAs(user, "/api/v1/categories/" + id).andExpect(status().isNoContent());
    }

    @Test
    void protectsSystemCategories() throws Exception {
        TestUser user = register("Ana");
        String groceries = jdbc.queryForObject("select id from categories where slug = 'groceries'", String.class);

        patchAs(user, "/api/v1/categories/" + groceries, Map.of("name", "Food"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("category_read_only"));
    }

    @Test
    void validatesColours() throws Exception {
        TestUser user = register("Ana");

        postAs(
                        user,
                        "/api/v1/categories",
                        Map.of("name", "Pets", "group", "ESSENTIALS", "icon", "paw", "colour", "orange"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("colour"));
    }
}
