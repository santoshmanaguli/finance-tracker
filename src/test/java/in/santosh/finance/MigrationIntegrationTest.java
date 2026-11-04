package in.santosh.finance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.annotation.Transactional;

/**
 * The schema is code, so it gets tests. These prove the constraints that our
 * dedup and security guarantees rely on actually exist in the real database.
 *
 * {@code @Transactional} on a test class = each test runs in a transaction that
 * is rolled back afterwards, so tests don't leak rows into each other.
 */
@IntegrationTest
@Transactional
class MigrationIntegrationTest {

    @Autowired
    JdbcClient jdbc;

    long accountId;
    long categoryId;

    @BeforeEach
    void setUp() {
        accountId = jdbc.sql("""
                INSERT INTO accounts (institution, account_type, last4)
                VALUES ('HDFC', 'CREDIT_CARD', '1234') RETURNING id""")
                .query(Long.class).single();
        categoryId = jdbc.sql("INSERT INTO categories (name) VALUES ('TestCategory') RETURNING id")
                .query(Long.class).single();
    }

    @Test
    void flywayAppliedAllMigrations() {
        Integer failed = jdbc.sql("SELECT count(*) FROM flyway_schema_history WHERE NOT success")
                .query(Integer.class).single();
        assertThat(failed).isZero();
    }

    @Test
    void accountRejectsAnythingButFourDigits() {
        assertThatThrownBy(() -> jdbc.sql("""
                INSERT INTO accounts (institution, account_type, last4)
                VALUES ('SBI', 'DEBIT_CARD', '12AB')""").update())
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void amountMustBePositivePaise() {
        assertThatThrownBy(() -> insertTransaction(-500, "REF1"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void sameReferenceOnSameAccountAndDirectionIsRejected() {
        insertTransaction(49900, "UPI123456789012");

        assertThatThrownBy(() -> insertTransaction(49900, "UPI123456789012"))
                .isInstanceOf(DuplicateKeyException.class);
    }

    @Test
    void transactionsWithoutReferenceAreNotConstrained() {
        // Two identical Rs 20 chai payments with no ref are both legitimate.
        insertTransaction(2000, null);
        insertTransaction(2000, null);

        Integer count = jdbc.sql("SELECT count(*) FROM transactions WHERE account_id = ?")
                .param(accountId).query(Integer.class).single();
        assertThat(count).isEqualTo(2);
    }

    @Test
    void identicalRawMessageIsRejectedByContentHash() {
        String hash = "a".repeat(64);
        insertUnparsedRaw(hash);

        assertThatThrownBy(() -> insertUnparsedRaw(hash)).isInstanceOf(DuplicateKeyException.class);
    }

    @Test
    void unparsedMessagesViewShowsOnlyUnparsed() {
        insertUnparsedRaw("b".repeat(64));

        Integer count = jdbc.sql("SELECT count(*) FROM unparsed_messages").query(Integer.class).single();
        assertThat(count).isEqualTo(1);
    }

    @Test
    @Disabled("TODO(santosh): enable after writing V2__seed_categories.sql")
    void uncategorizedCategoryIsSeeded() {
        // TODO(santosh): query categories for 'Uncategorized' and assert it exists.
        // Hint: jdbc.sql("SELECT count(*) FROM categories WHERE name = ?").param(...)
        //           .query(Integer.class).single()
        // Bonus: assert every SYSTEM merchant_rule points at an existing category
        // and that at least 10 rules were seeded.
    }

    private void insertTransaction(long amountPaise, String ref) {
        jdbc.sql("""
                INSERT INTO transactions (account_id, amount_paise, direction, reference_no, txn_time,
                                          category_id, category_source, source, raw_text)
                VALUES (:account, :amount, 'DEBIT', :ref, now(), :category, 'DEFAULT', 'SMS', 'test')""")
                .param("account", accountId)
                .param("amount", amountPaise)
                .param("ref", ref)
                .param("category", categoryId)
                .update();
    }

    private void insertUnparsedRaw(String hash) {
        jdbc.sql("""
                INSERT INTO raw_messages (source, sender, body, received_at, content_hash, parse_status)
                VALUES ('SMS', 'AX-HDFCBK', 'some text', now(), ?, 'UNPARSED')""")
                .param(hash)
                .update();
    }
}
