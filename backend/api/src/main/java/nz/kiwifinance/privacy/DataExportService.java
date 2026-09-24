package nz.kiwifinance.privacy;

import java.sql.Timestamp;
import java.time.Clock;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Gathers everything Kiwi Finance stores about a person, as the Privacy Act 2020 entitles them to
 * request. Reading the tables directly keeps the export complete as features are added. Secrets are
 * left out: the password hash, refresh tokens, encrypted bank-feed credentials and OAuth state.
 */
@Service
@RequiredArgsConstructor
class DataExportService {

    private static final Map<String, String> QUERIES = queries();

    private final JdbcTemplate jdbc;
    private final Clock clock;

    @Transactional(readOnly = true)
    public DataExport export(UUID userId) {
        Map<String, List<Map<String, Object>>> tables = new LinkedHashMap<>();
        QUERIES.forEach((name, sql) -> tables.put(
                name,
                jdbc.queryForList(sql, userId).stream()
                        .map(DataExportService::portable)
                        .toList()));
        return new DataExport(clock.instant(), tables);
    }

    private static Map<String, Object> portable(Map<String, Object> row) {
        Map<String, Object> converted = new LinkedHashMap<>();
        row.forEach((column, value) -> converted.put(column, portable(value)));
        return converted;
    }

    private static Object portable(Object value) {
        return switch (value) {
            case null -> null;
            case Timestamp timestamp -> timestamp.toInstant().toString();
            case java.sql.Date date -> date.toLocalDate().toString();
            case UUID id -> id.toString();
            default -> value;
        };
    }

    private static Map<String, String> queries() {
        Map<String, String> queries = new LinkedHashMap<>();
        queries.put("user", "SELECT id, email, display_name, created_at, updated_at FROM users WHERE id = ?");
        queries.put("profile", "SELECT * FROM user_profiles WHERE user_id = ?");
        queries.put("accounts", "SELECT * FROM accounts WHERE user_id = ? ORDER BY created_at");
        queries.put("categories", "SELECT * FROM categories WHERE user_id = ? ORDER BY created_at");
        queries.put("categorisationRules", "SELECT * FROM categorisation_rules WHERE user_id = ? ORDER BY priority");
        queries.put("transactions", "SELECT * FROM transactions WHERE user_id = ? ORDER BY posted_on, created_at");
        queries.put("incomeSources", "SELECT * FROM income_sources WHERE user_id = ? ORDER BY created_at");
        queries.put("imports", "SELECT * FROM import_batches WHERE user_id = ? ORDER BY created_at");
        queries.put("bankConnections", """
                SELECT id, provider, method, status, external_user_id, last_synced_at, disconnected_at,
                       created_at, updated_at
                FROM bank_connections WHERE user_id = ? ORDER BY created_at""");
        queries.put("bankFeedAccounts", "SELECT * FROM bank_feed_accounts WHERE user_id = ? ORDER BY created_at");
        queries.put("bankSyncRuns", "SELECT * FROM bank_sync_runs WHERE user_id = ? ORDER BY started_at");
        queries.put("budgets", "SELECT * FROM budgets WHERE user_id = ? ORDER BY created_at");
        queries.put("budgetLines", """
                SELECT l.* FROM budget_lines l JOIN budgets b ON b.id = l.budget_id
                WHERE b.user_id = ? ORDER BY l.created_at""");
        queries.put("goals", "SELECT * FROM goals WHERE user_id = ? ORDER BY created_at");
        queries.put("goalContributions", "SELECT * FROM goal_contributions WHERE user_id = ? ORDER BY contributed_on");
        queries.put("purchasePlans", "SELECT * FROM purchase_plans WHERE user_id = ? ORDER BY created_at");
        queries.put("moneyMovements", "SELECT * FROM money_movements WHERE user_id = ? ORDER BY moved_on, created_at");
        queries.put("preferences", "SELECT * FROM user_preferences WHERE user_id = ?");
        return Collections.unmodifiableMap(queries);
    }
}
