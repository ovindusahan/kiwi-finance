package nz.kiwifinance.imports;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;
import nz.kiwifinance.support.IntegrationTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.ResultActions;

class ImportIntegrationTest extends IntegrationTestBase {

    private static final String EXPORT = """
            Date,Amount,Other Party,Description,Reference,Particulars,Analysis Code
            03/09/2026,-15.99,Spotify,DEBIT CARD PURCHASE,,,
            04/09/2026,-4.50,Mojo,DEBIT CARD PURCHASE,,,
            04/09/2026,-4.50,Mojo,DEBIT CARD PURCHASE,,,
            05/09/2026,2650.00,Acme Ltd,SALARY,,,
            """;

    @Test
    void importsOnceAndRecognisesOverlappingExports() throws Exception {
        TestUser user = register("Ana");
        UUID account = idOf(postAs(user, "/api/v1/accounts", Map.of("name", "Westpac Everyday", "type", "EVERYDAY")));

        upload(user, account, EXPORT)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.format").value("WESTPAC"))
                .andExpect(jsonPath("$.rowCount").value(4))
                .andExpect(jsonPath("$.importedCount").value(4))
                .andExpect(jsonPath("$.duplicateCount").value(0));
        upload(user, account, EXPORT + "06/09/2026,-80.00,Z Energy,FUEL,,,\n")
                .andExpect(jsonPath("$.importedCount").value(1))
                .andExpect(jsonPath("$.duplicateCount").value(4));

        getAs(user, "/api/v1/transactions?accountId=" + account)
                .andExpect(jsonPath("$.items.length()").value(5))
                .andExpect(jsonPath("$.items[0].source").value("CSV_IMPORT"));
        getAs(user, "/api/v1/imports").andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    void refusesImportsIntoSomeoneElsesAccount() throws Exception {
        TestUser owner = register("Owner");
        TestUser other = register("Other");
        UUID account = idOf(postAs(owner, "/api/v1/accounts", Map.of("name", "Everyday", "type", "EVERYDAY")));

        upload(other, account, EXPORT).andExpect(status().isNotFound());
    }

    @Test
    void explainsUnrecognisedFiles() throws Exception {
        TestUser user = register("Ana");
        UUID account = idOf(postAs(user, "/api/v1/accounts", Map.of("name", "Everyday", "type", "EVERYDAY")));

        upload(user, account, "hello,world\n1,2\n")
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("import_format_unrecognised"));
    }

    private ResultActions upload(TestUser user, UUID account, String content) {
        var file = new MockMultipartFile("file", "export.csv", "text/csv", content.getBytes(StandardCharsets.UTF_8));
        return perform(
                multipart("/api/v1/imports").file(file).param("accountId", account.toString()), user.token(), null);
    }
}
