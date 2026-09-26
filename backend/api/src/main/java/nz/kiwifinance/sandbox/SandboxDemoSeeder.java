package nz.kiwifinance.sandbox;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.log4j.Log4j2;
import nz.kiwifinance.engine.time.NzTime;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;

/**
 * Creates a demo account in the sandbox by using the public API exactly as a person would:
 * signing up, connecting Akahu, choosing accounts, syncing and setting up a budget and goals.
 */
@Profile("sandbox")
@Component
@Log4j2
class SandboxDemoSeeder {

    static final String EMAIL = "demo@kiwifinance.nz";
    static final String PASSWORD = "kiwi-demo-2026";

    private static final Duration SYNC_TIMEOUT = Duration.ofSeconds(60);

    private final Environment environment;
    private final RestClient.Builder restClientBuilder;
    private final Clock clock;
    private final boolean enabled;

    SandboxDemoSeeder(
            Environment environment,
            RestClient.Builder restClientBuilder,
            Clock clock,
            @Value("${kiwi.sandbox.seed-demo:true}") boolean enabled) {
        this.environment = environment;
        this.restClientBuilder = restClientBuilder;
        this.clock = clock;
        this.enabled = enabled;
    }

    @EventListener(ApplicationReadyEvent.class)
    void seed() {
        if (!enabled) {
            return;
        }
        String port = environment.getProperty("local.server.port", "8080");
        RestClient anonymous = restClientBuilder
                .clone()
                .baseUrl("http://localhost:" + port + "/api/v1")
                .build();
        String token;
        try {
            token = anonymous
                    .post()
                    .uri("/auth/register")
                    .body(Map.of("email", EMAIL, "password", PASSWORD, "displayName", "Aroha"))
                    .retrieve()
                    .body(JsonNode.class)
                    .get("accessToken")
                    .asString();
        } catch (HttpClientErrorException e) {
            if (e.getStatusCode() == HttpStatus.CONFLICT) {
                log.info("Demo account already exists: {}", EMAIL);
                return;
            }
            throw e;
        }
        RestClient api = restClientBuilder
                .clone()
                .baseUrl("http://localhost:" + port + "/api/v1")
                .defaultHeaders(headers -> headers.setBearerAuth(token))
                .build();
        try {
            populate(api);
            log.info("Demo account ready. Sign in as {} with password {}", EMAIL, PASSWORD);
        } catch (RuntimeException e) {
            log.error("Could not finish setting up the demo account", e);
        }
    }

    private void populate(RestClient api) {
        LocalDate today = NzTime.today(clock);
        Map<String, Object> profile = new HashMap<>();
        profile.put("region", "AUCKLAND");
        profile.put("householdSize", 2);
        profile.put("dependants", 0);
        profile.put("employmentType", "EMPLOYEE");
        profile.put("housingType", "RENTING");
        profile.put("singleIncomeHousehold", false);
        profile.put("taxCode", "M");
        profile.put("hasStudentLoan", true);
        profile.put("kiwiSaverMember", true);
        profile.put("kiwiSaverRate", 0.035);
        profile.put("kiwiSaverJoinedOn", "2019-03-01");
        profile.put("kiwiSaverBalanceCents", 2_485_000);
        profile.put("firstHomeBuyer", true);
        profile.put("payFrequency", "FORTNIGHTLY");
        profile.put("savingsInterestRate", 0.03);
        profile.put("targetSavingsRate", 0.15);
        api.put().uri("/profile").body(profile).retrieve().toBodilessEntity();

        api.post()
                .uri("/income-sources")
                .body(Map.of(
                        "name",
                        "Acme Ltd salary",
                        "type",
                        "SALARY",
                        "amountCents",
                        265_000,
                        "basis",
                        "NET",
                        "frequency",
                        "FORTNIGHTLY"))
                .retrieve()
                .toBodilessEntity();

        String rent = category(api, "rent");
        api.post()
                .uri("/categorisation-rules")
                .body(Map.of("matchType", "STARTS_WITH", "pattern", "RENT", "categoryId", rent, "priority", 10))
                .retrieve()
                .toBodilessEntity();
        api.post()
                .uri("/categorisation-rules")
                .body(Map.of(
                        "matchType",
                        "CONTAINS",
                        "pattern",
                        "SALARY",
                        "categoryId",
                        category(api, "salary"),
                        "priority",
                        10))
                .retrieve()
                .toBodilessEntity();

        JsonNode connection = api.post()
                .uri("/bank-feeds/akahu/personal-connections")
                .body(Map.of("appToken", "app_token_sandbox_demo", "userToken", "user_token_sandbox_demo"))
                .retrieve()
                .body(JsonNode.class);
        String connectionId = connection.get("id").asString();
        for (JsonNode account : connection.get("accounts")) {
            api.patch()
                    .uri(
                            "/bank-feeds/connections/{id}/accounts/{feedAccountId}",
                            connectionId,
                            account.get("id").asString())
                    .body(Map.of("syncEnabled", true))
                    .retrieve()
                    .toBodilessEntity();
        }
        awaitSync(api, connectionId);
        api.post()
                .uri("/bank-feeds/connections/{id}/syncs", connectionId)
                .retrieve()
                .toBodilessEntity();
        awaitSync(api, connectionId);

        for (JsonNode account : api.get().uri("/accounts").retrieve().body(JsonNode.class)) {
            if ("SAVINGS".equals(account.get("type").asString())) {
                api.put()
                        .uri("/emergency-fund/account")
                        .body(Map.of("accountId", account.get("id").asString()))
                        .retrieve()
                        .toBodilessEntity();
                break;
            }
        }

        JsonNode recommendation =
                api.get().uri("/budgets/recommendation").retrieve().body(JsonNode.class);
        List<Map<String, Object>> lines = new ArrayList<>();
        for (JsonNode line : recommendation.get("lines")) {
            Map<String, Object> entry = new HashMap<>();
            entry.put(
                    "categoryId",
                    line.get("category").isNull()
                            ? null
                            : line.get("category").get("id").asString());
            entry.put("limitCents", line.get("recommended").get("cents").asLong());
            entry.put("rationale", line.get("rationale").asString());
            lines.add(entry);
        }
        api.post()
                .uri("/budgets")
                .body(Map.of("name", "Everyday budget", "lines", lines))
                .retrieve()
                .toBodilessEntity();

        goal(api, "Trip to Japan", "TRAVEL", 600_000, today.plusMonths(10), 1, 25_000, 120_000);
        goal(api, "New laptop", "PURCHASE", 250_000, today.plusMonths(5), 2, 15_000, 180_000);
        goal(api, "House deposit", "HOUSE_DEPOSIT", 8_000_000, today.plusYears(5), 3, 0, 0);

        Map<String, Object> plan = new HashMap<>();
        plan.put("itemName", "a Toyota Aqua hybrid");
        plan.put("priceCents", 1_800_000);
        plan.put("desiredDate", today.plusMonths(6).toString());
        plan.put("funding", "FINANCE");
        plan.put("depositCents", 500_000);
        plan.put("loanRate", 0.099);
        plan.put("loanTermMonths", 48);
        plan.put("loanFeesCents", 35_000);
        plan.put("firstHome", false);
        api.post().uri("/planning/purchase-plans").body(plan).retrieve().toBodilessEntity();
    }

    private static void goal(
            RestClient api,
            String name,
            String type,
            long target,
            LocalDate date,
            int priority,
            long monthly,
            long starting) {
        api.post()
                .uri("/goals")
                .body(Map.of(
                        "name", name,
                        "type", type,
                        "targetCents", target,
                        "targetDate", date.toString(),
                        "priority", priority,
                        "monthlyContributionCents", monthly,
                        "startingAmountCents", starting))
                .retrieve()
                .toBodilessEntity();
    }

    private static String category(RestClient api, String slug) {
        for (JsonNode category : api.get().uri("/categories").retrieve().body(JsonNode.class)) {
            if (slug.equals(category.get("slug").asString(""))) {
                return category.get("id").asString();
            }
        }
        throw new IllegalStateException("Missing system category " + slug);
    }

    private void awaitSync(RestClient api, String connectionId) {
        long deadline = System.nanoTime() + SYNC_TIMEOUT.toNanos();
        while (System.nanoTime() < deadline) {
            JsonNode runs = api.get()
                    .uri("/bank-feeds/connections/{id}/syncs", connectionId)
                    .retrieve()
                    .body(JsonNode.class);
            JsonNode connection = api.get()
                    .uri("/bank-feeds/connections/{id}", connectionId)
                    .retrieve()
                    .body(JsonNode.class);
            boolean running = runs.size() > 0
                    && "RUNNING".equals(runs.get(0).get("status").asString());
            if (!running && !connection.get("lastSyncedAt").isNull()) {
                return;
            }
            try {
                Thread.sleep(250);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
        throw new IllegalStateException("Sandbox sync did not finish in time");
    }
}
