package nz.kiwifinance.sandbox;

import java.time.Clock;
import java.time.Instant;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import nz.kiwifinance.engine.time.NzTime;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * A stand-in for the Akahu API with the same shapes and authentication rules, so the full bank
 * connection journey can be tried and tested without real bank data. Any token that starts with
 * {@code user_token_sandbox} and app token that starts with {@code app_token_sandbox} is accepted.
 */
@Profile("sandbox")
@RestController
@RequestMapping("/sandbox/akahu/v1")
@RequiredArgsConstructor
class SandboxAkahuController {

    static final String OAUTH_CODE = "sandbox_authorisation_code";
    private static final String USER_PREFIX = "Bearer user_token_sandbox";
    private static final String APP_PREFIX = "app_token_sandbox";
    private static final int PAGE_SIZE = 100;

    private final Clock clock;

    @GetMapping("/me")
    ResponseEntity<Map<String, Object>> me(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestHeader(value = "X-Akahu-Id", required = false) String app) {
        if (!authorised(authorization, app)) {
            return unauthorised();
        }
        String userId = "sandbox_user_" + Integer.toHexString(authorization.hashCode() & 0xfffff);
        return ResponseEntity.ok(Map.of(
                "success",
                true,
                "item",
                Map.of("_id", userId, "email", "demo@kiwifinance.nz", "access_granted_at", "2026-01-01T00:00:00Z")));
    }

    @GetMapping("/accounts")
    ResponseEntity<Map<String, Object>> accounts(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestHeader(value = "X-Akahu-Id", required = false) String app) {
        if (!authorised(authorization, app)) {
            return unauthorised();
        }
        Instant refreshed = clock.instant();
        List<Map<String, Object>> items = dataset().accounts().stream()
                .map(account -> {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("_id", account.id());
                    item.put("name", account.name());
                    item.put("status", "ACTIVE");
                    item.put("type", account.type());
                    item.put("formatted_account", account.formatted());
                    item.put(
                            "connection",
                            Map.of("_id", "conn_" + account.bank().toLowerCase(), "name", account.bank()));
                    item.put("balance", Map.of("current", Double.valueOf(account.balance()), "currency", "NZD"));
                    item.put("attributes", account.attributes());
                    item.put(
                            "refreshed", Map.of("balance", refreshed.toString(), "transactions", refreshed.toString()));
                    return item;
                })
                .toList();
        return ResponseEntity.ok(Map.of("success", true, "items", items));
    }

    @GetMapping("/accounts/{accountId}/transactions")
    ResponseEntity<Map<String, Object>> transactions(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestHeader(value = "X-Akahu-Id", required = false) String app,
            @PathVariable String accountId,
            @RequestParam Instant start,
            @RequestParam Instant end,
            @RequestParam(required = false) String cursor) {
        if (!authorised(authorization, app)) {
            return unauthorised();
        }
        List<SandboxDataset.Transaction> matching = dataset().transactions(accountId).stream()
                .filter(t -> t.instant().isAfter(start) && !t.instant().isAfter(end))
                .toList();
        int offset = cursor == null ? 0 : Integer.parseInt(cursor);
        int to = Math.min(matching.size(), offset + PAGE_SIZE);
        List<Map<String, Object>> items = matching.subList(Math.min(offset, to), to).stream()
                .map(SandboxAkahuController::toJson)
                .toList();
        Map<String, Object> page = new HashMap<>();
        page.put("success", true);
        page.put("items", items);
        Map<String, Object> next = new HashMap<>();
        next.put("next", to < matching.size() ? String.valueOf(to) : null);
        page.put("cursor", next);
        return ResponseEntity.ok(page);
    }

    @PostMapping("/token")
    ResponseEntity<Map<String, Object>> token(@RequestBody Map<String, String> body) {
        if (!OAUTH_CODE.equals(body.get("code"))) {
            return unauthorised();
        }
        return ResponseEntity.ok(Map.of(
                "success",
                true,
                "access_token",
                "user_token_sandbox_oauth",
                "token_type",
                "bearer",
                "scope",
                "ENDURING_CONSENT"));
    }

    @DeleteMapping("/token")
    ResponseEntity<Map<String, Object>> revoke(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestHeader(value = "X-Akahu-Id", required = false) String app) {
        return authorised(authorization, app) ? ResponseEntity.ok(Map.of("success", true)) : unauthorised();
    }

    private SandboxDataset dataset() {
        return new SandboxDataset(NzTime.today(clock));
    }

    private static boolean authorised(String authorization, String app) {
        return authorization != null
                && authorization.startsWith(USER_PREFIX)
                && app != null
                && app.startsWith(APP_PREFIX);
    }

    private static ResponseEntity<Map<String, Object>> unauthorised() {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("success", false, "message", "Unauthorized"));
    }

    private static Map<String, Object> toJson(SandboxDataset.Transaction transaction) {
        Map<String, Object> json = new LinkedHashMap<>();
        json.put("_id", transaction.id());
        json.put("_account", transaction.account());
        json.put("_connection", "conn_sandbox");
        json.put("date", transaction.instant().toString());
        json.put("description", transaction.description());
        json.put("amount", transaction.amount());
        json.put("type", transaction.type());
        if (transaction.merchant() != null) {
            json.put(
                    "merchant",
                    Map.of("_id", "merchant_" + transaction.merchant().hashCode(), "name", transaction.merchant()));
        }
        if (transaction.category() != null) {
            json.put(
                    "category",
                    Map.of("_id", "cat_" + transaction.category().hashCode(), "name", transaction.category()));
        }
        return json;
    }
}
