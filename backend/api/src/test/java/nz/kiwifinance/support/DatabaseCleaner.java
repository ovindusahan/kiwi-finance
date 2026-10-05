package nz.kiwifinance.support;

import java.time.Duration;
import org.springframework.jdbc.core.JdbcTemplate;

final class DatabaseCleaner {

    private DatabaseCleaner() {}

    /**
     * Removes every user and, through cascading foreign keys, everything they own. A row-level
     * delete is used because TRUNCATE ... CASCADE would also empty the shared system categories.
     */
    static void clean(JdbcTemplate jdbc) {
        awaitBankSyncs(jdbc);
        jdbc.execute("DELETE FROM users");
    }

    /**
     * Waits for bank syncs a previous test started in the background, so they don't write to
     * rows this delete is removing. A run stays RUNNING until its outcome, and its lease release,
     * are committed together.
     */
    private static void awaitBankSyncs(JdbcTemplate jdbc) {
        long deadline = System.nanoTime() + Duration.ofSeconds(10).toNanos();
        while (System.nanoTime() < deadline
                && jdbc.queryForObject("SELECT count(*) FROM bank_sync_runs WHERE status = 'RUNNING'", Integer.class)
                        > 0) {
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }
}
