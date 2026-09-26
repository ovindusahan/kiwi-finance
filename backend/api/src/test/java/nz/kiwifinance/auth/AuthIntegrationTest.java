package nz.kiwifinance.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;
import nz.kiwifinance.support.IntegrationTestBase;
import org.junit.jupiter.api.Test;

class AuthIntegrationTest extends IntegrationTestBase {

    @Test
    void registersAndReturnsTheCurrentUser() throws Exception {
        TestUser user = register("Aroha Ngata");

        getAs(user, "/api/v1/auth/me")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(user.email()))
                .andExpect(jsonPath("$.displayName").value("Aroha Ngata"));
        getAs(user, "/api/v1/profile")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.taxCode").value("M"))
                .andExpect(jsonPath("$.onboarded").value(false));
    }

    @Test
    void rejectsDuplicateEmailsCaseInsensitively() throws Exception {
        TestUser user = register("Tama");

        postAs(
                        null,
                        "/api/v1/auth/register",
                        Map.of(
                                "email",
                                user.email().toUpperCase(),
                                "password",
                                "another-long-password",
                                "displayName",
                                "Tama"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("email_already_registered"));
    }

    @Test
    void validatesRegistration() throws Exception {
        postAs(null, "/api/v1/auth/register", Map.of("email", "not-an-email", "password", "short", "displayName", ""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("validation_failed"))
                .andExpect(jsonPath("$.errors.length()").value(3));
    }

    @Test
    void locksTheAccountAfterRepeatedFailures() throws Exception {
        TestUser user = register("Mere");
        for (int i = 0; i < 5; i++) {
            postAs(null, "/api/v1/auth/login", Map.of("email", user.email(), "password", "wrong-password-123"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("invalid_credentials"));
        }

        postAs(null, "/api/v1/auth/login", Map.of("email", user.email(), "password", "a-long-test-password"))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("too_many_login_attempts"));
    }

    @Test
    void doesNotRevealWhetherAnEmailIsRegistered() throws Exception {
        postAs(null, "/api/v1/auth/login", Map.of("email", "nobody@example.nz", "password", "whatever-password"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("Email or password is incorrect."));
    }

    @Test
    void refreshTokenReuseRevokesTheWholeFamily() throws Exception {
        TestUser user = register("Hemi");
        var first = bodyOf(postAs(null, "/api/v1/auth/refresh", Map.of("refreshToken", user.refreshToken()))
                .andExpect(status().isOk()));
        String rotated = first.get("refreshToken").asString();
        assertThat(rotated).isNotEqualTo(user.refreshToken());

        postAs(null, "/api/v1/auth/refresh", Map.of("refreshToken", user.refreshToken()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("invalid_refresh_token"));
        postAs(null, "/api/v1/auth/refresh", Map.of("refreshToken", rotated)).andExpect(status().isUnauthorized());
    }

    @Test
    void logoutEndsTheSession() throws Exception {
        TestUser user = register("Kiri");

        postAs(null, "/api/v1/auth/logout", Map.of("refreshToken", user.refreshToken()))
                .andExpect(status().isNoContent());
        postAs(null, "/api/v1/auth/refresh", Map.of("refreshToken", user.refreshToken()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void requiresAuthenticationWithProblemDetails() throws Exception {
        perform(get("/api/v1/accounts"), null, null)
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("WWW-Authenticate", "Bearer"))
                .andExpect(jsonPath("$.code").value("unauthenticated"));
        perform(get("/api/v1/accounts"), "not-a-jwt", null).andExpect(status().isUnauthorized());
    }

    @Test
    void deletesTheAccountAndAllData() throws Exception {
        TestUser user = register("Wiremu");
        postAs(user, "/api/v1/accounts", Map.of("name", "Everyday", "type", "EVERYDAY"))
                .andExpect(status().isCreated());

        deleteAs(user, "/api/v1/auth/me", Map.of("password", "wrong-password-123"))
                .andExpect(status().isUnauthorized());
        deleteAs(user, "/api/v1/auth/me", Map.of("password", "a-long-test-password"))
                .andExpect(status().isNoContent());

        assertThat(jdbc.queryForObject("select count(*) from accounts", Integer.class))
                .isZero();
        getAs(user, "/api/v1/auth/me").andExpect(status().isUnauthorized());
    }

    @Test
    void updatesNameAndEmail() throws Exception {
        TestUser user = register("Aroha");
        TestUser other = register("Tama");

        patchAs(user, "/api/v1/auth/me", Map.of("displayName", "Aroha Ngata"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.displayName").value("Aroha Ngata"))
                .andExpect(jsonPath("$.email").value(user.email()));
        patchAs(user, "/api/v1/auth/me", Map.of("email", "new-" + user.email()))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("wrong_password"));
        patchAs(user, "/api/v1/auth/me", Map.of("email", other.email(), "currentPassword", "a-long-test-password"))
                .andExpect(status().isConflict());
        patchAs(
                        user,
                        "/api/v1/auth/me",
                        Map.of("email", "New-" + user.email(), "currentPassword", "a-long-test-password"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("new-" + user.email()));
        postAs(null, "/api/v1/auth/login", Map.of("email", "new-" + user.email(), "password", "a-long-test-password"))
                .andExpect(status().isOk());
    }

    @Test
    void changesThePasswordAndEndsOtherSessions() throws Exception {
        TestUser user = register("Mere");

        postAs(
                        user,
                        "/api/v1/auth/me/password",
                        Map.of("currentPassword", "wrong-password-123", "newPassword", "another-long-password"))
                .andExpect(status().isUnprocessableContent());
        postAs(
                        user,
                        "/api/v1/auth/me/password",
                        Map.of("currentPassword", "a-long-test-password", "newPassword", "another-long-password"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.refreshToken").isNotEmpty());

        postAs(null, "/api/v1/auth/refresh", Map.of("refreshToken", user.refreshToken()))
                .andExpect(status().isUnauthorized());
        postAs(null, "/api/v1/auth/login", Map.of("email", user.email(), "password", "a-long-test-password"))
                .andExpect(status().isUnauthorized());
        postAs(null, "/api/v1/auth/login", Map.of("email", user.email(), "password", "another-long-password"))
                .andExpect(status().isOk());
    }
}
