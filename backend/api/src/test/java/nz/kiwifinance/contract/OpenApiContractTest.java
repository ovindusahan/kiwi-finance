package nz.kiwifinance.contract;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import nz.kiwifinance.support.IntegrationTestBase;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.SerializationFeature;

/**
 * Keeps {@code contracts/openapi.json} in step with the API. Clients generate their types from
 * that file, so an API change must update it in the same commit. Run the build with
 * {@code -Dcontract.update=true} to regenerate it.
 */
class OpenApiContractTest extends IntegrationTestBase {

    @Test
    void contractMatchesTheApi() throws Exception {
        JsonNode spec = bodyOf(perform(get("/api/v1/openapi.json"), null, null));
        String generated = json.writer()
                        .with(SerializationFeature.INDENT_OUTPUT)
                        .writeValueAsString(sorted(json.treeToValue(spec, Object.class)))
                + "\n";
        Path contract = Path.of(System.getProperty("contract.output", "../../contracts/openapi.json"));

        if (Boolean.parseBoolean(System.getProperty("contract.update", "false")) || !Files.exists(contract)) {
            write(contract, generated);
        }

        assertThat(Files.readString(contract, StandardCharsets.UTF_8))
                .as("contracts/openapi.json is out of date. Rebuild with -Dcontract.update=true and commit the result.")
                .isEqualTo(generated);
    }

    @SuppressWarnings("unchecked")
    private static Object sorted(Object value) {
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> result = new TreeMap<>();
            map.forEach((key, child) -> result.put(String.valueOf(key), sorted(child)));
            return result;
        }
        if (value instanceof List<?> list) {
            List<Object> result = new ArrayList<>(list.size());
            list.forEach(child -> result.add(sorted(child)));
            return Collections.unmodifiableList(result);
        }
        return value;
    }

    private static void write(Path contract, String content) throws IOException {
        Files.createDirectories(contract.getParent());
        Files.writeString(contract, content, StandardCharsets.UTF_8);
    }
}
