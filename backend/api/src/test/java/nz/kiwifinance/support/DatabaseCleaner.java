package nz.kiwifinance.support;

import org.springframework.jdbc.core.JdbcTemplate;

final class DatabaseCleaner {

    private DatabaseCleaner() {}

    /**
     * Removes every user and, through cascading foreign keys, everything they own. A row-level
     * delete is used because TRUNCATE ... CASCADE would also empty the shared system categories.
     */
    static void clean(JdbcTemplate jdbc) {
        jdbc.execute("DELETE FROM users");
    }
}
