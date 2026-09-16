package nz.kiwifinance.support;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.AbstractMockHttpServletRequestBuilder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@IntegrationTest
public abstract class IntegrationTestBase {

    @Autowired
    protected MockMvc mvc;

    @Autowired
    protected JsonMapper json;

    @Autowired
    protected JdbcTemplate jdbc;

    @BeforeEach
    void cleanDatabase() {
        DatabaseCleaner.clean(jdbc);
    }

    protected TestUser register(String name) {
        String email = name.toLowerCase().replace(' ', '.') + "+" + UUID.randomUUID() + "@example.nz";
        JsonNode body = readJson(perform(
                        post("/api/v1/auth/register"),
                        null,
                        Map.of("email", email, "password", "a-long-test-password", "displayName", name))
                .andReturn());
        String token = body.get("accessToken").asString();
        JsonNode me = readJson(perform(get("/api/v1/auth/me"), token, null).andReturn());
        return new TestUser(
                UUID.fromString(me.get("id").asString()),
                email,
                token,
                body.get("refreshToken").asString());
    }

    protected ResultActions getAs(TestUser user, String path) {
        return perform(get(path), user.token(), null);
    }

    protected ResultActions postAs(TestUser user, String path, Object body) {
        return perform(post(path), user == null ? null : user.token(), body);
    }

    protected ResultActions putAs(TestUser user, String path, Object body) {
        return perform(put(path), user.token(), body);
    }

    protected ResultActions patchAs(TestUser user, String path, Object body) {
        return perform(patch(path), user.token(), body);
    }

    protected ResultActions deleteAs(TestUser user, String path) {
        return perform(delete(path), user.token(), null);
    }

    protected ResultActions deleteAs(TestUser user, String path, Object body) {
        return perform(delete(path), user.token(), body);
    }

    protected JsonNode readJson(MvcResult result) {
        try {
            return json.readTree(result.getResponse().getContentAsString());
        } catch (java.io.UnsupportedEncodingException e) {
            throw new IllegalStateException(e);
        }
    }

    protected JsonNode bodyOf(ResultActions actions) {
        return readJson(actions.andReturn());
    }

    protected UUID idOf(ResultActions actions) {
        return UUID.fromString(bodyOf(actions).get("id").asString());
    }

    protected ResultActions perform(AbstractMockHttpServletRequestBuilder<?> request, String token, Object body) {
        try {
            if (token != null) {
                request.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
            }
            if (body != null) {
                request.contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body));
            }
            return mvc.perform(request);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    public record TestUser(UUID id, String email, String token, String refreshToken) {}
}
