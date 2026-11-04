package in.santosh.finance;

import org.springframework.jdbc.core.simple.JdbcClient;

/** Shared fixture helpers. Idempotent, so they work whether or not V2 seed data exists. */
public final class TestData {

    private TestData() {
    }

    /** Wipe everything ingest writes (for tests that commit, i.e. real HTTP calls). */
    public static void resetIngestTables(JdbcClient jdbc) {
        jdbc.sql("TRUNCATE raw_messages, transactions, accounts, merchant_rules RESTART IDENTITY CASCADE").update();
    }

    public static void ensureCategory(JdbcClient jdbc, String name) {
        jdbc.sql("INSERT INTO categories (name) VALUES (?) ON CONFLICT (name) DO NOTHING").param(name).update();
    }

    public static void ensureRule(JdbcClient jdbc, String pattern, String matchType, String category, int priority) {
        ensureCategory(jdbc, category);
        jdbc.sql("""
                INSERT INTO merchant_rules (pattern, match_type, category_id, priority, created_by)
                SELECT ?, ?, id, ?, 'SYSTEM' FROM categories WHERE name = ?
                ON CONFLICT (pattern, match_type) DO NOTHING""")
                .params(pattern, matchType, priority, category)
                .update();
    }
}
