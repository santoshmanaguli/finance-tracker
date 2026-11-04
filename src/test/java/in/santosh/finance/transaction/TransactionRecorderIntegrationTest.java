package in.santosh.finance.transaction;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.annotation.Transactional;

import in.santosh.finance.IntegrationTest;
import in.santosh.finance.TestData;
import in.santosh.finance.account.Account;
import in.santosh.finance.account.AccountService;
import in.santosh.finance.account.AccountType;
import in.santosh.finance.transaction.TransactionRecorder.Observation;

/**
 * Dedup layers 2 and 3, tested directly against the recorder.
 * {@code @Transactional}: each test rolls back, and it satisfies the recorder's
 * Propagation.MANDATORY requirement.
 */
@IntegrationTest
@Transactional
class TransactionRecorderIntegrationTest {

    static final Instant T0 = Instant.parse("2026-11-04T04:45:00Z");

    @Autowired
    TransactionRecorder recorder;

    @Autowired
    AccountService accounts;

    @Autowired
    JdbcClient jdbc;

    Account card;

    @BeforeEach
    void setUp() {
        TestData.resetIngestTables(jdbc);
        TestData.ensureCategory(jdbc, "Uncategorized");
        card = accounts.findOrCreate("HDFC", AccountType.CREDIT_CARD, "1234");
    }

    // ---------- layer 2: reference number ----------

    @Test
    void sameReferenceFromAnotherSourceMatches() {
        var sms = recorder.record(obs(50000, "UPI123", T0, Source.SMS));
        var email = recorder.record(obs(50000, "UPI123", T0.plus(Duration.ofMinutes(2)), Source.EMAIL));

        assertThat(email.created()).isFalse();
        assertThat(email.transaction().getId()).isEqualTo(sms.transaction().getId());
    }

    @Test
    void differentReferencesAreDifferentTransactionsEvenIfEverythingElseMatches() {
        recorder.record(obs(50000, "UPI111", T0, Source.SMS));
        var other = recorder.record(obs(50000, "UPI222", T0, Source.EMAIL));

        assertThat(other.created()).isTrue();
    }

    // ---------- layer 3: fuzzy, cross-source ----------

    @Test
    void refLessSmsAndEmailWithinWindowMatchAndEmailRefIsAdopted() {
        var sms = recorder.record(obs(9900, null, T0, Source.SMS));
        link(sms.transaction(), Source.SMS);

        var email = recorder.record(obs(9900, "AUTH789", T0.plus(Duration.ofMinutes(10)), Source.EMAIL));

        assertThat(email.created()).isFalse();
        assertThat(email.transaction().getId()).isEqualTo(sms.transaction().getId());
        assertThat(email.transaction().getReferenceNo()).isEqualTo("AUTH789");
    }

    @Test
    void outsideWindowIsANewTransaction() {
        var sms = recorder.record(obs(9900, null, T0, Source.SMS));
        link(sms.transaction(), Source.SMS);

        var email = recorder.record(obs(9900, null, T0.plus(Duration.ofMinutes(16)), Source.EMAIL));

        assertThat(email.created()).isTrue();
    }

    @Test
    void sameSourceNeverFuzzyMatches() {
        var first = recorder.record(obs(2000, null, T0, Source.SMS));
        link(first.transaction(), Source.SMS);

        var second = recorder.record(obs(2000, null, T0.plus(Duration.ofMinutes(5)), Source.SMS));

        assertThat(second.created()).as("two real Rs 20 chai payments").isTrue();
    }

    @Test
    void twoSwipesAndTwoEmailsPairUpOneToOneByClosestTime() {
        var swipe1 = recorder.record(obs(2000, null, T0, Source.SMS));
        link(swipe1.transaction(), Source.SMS);
        var swipe2 = recorder.record(obs(2000, null, T0.plus(Duration.ofMinutes(5)), Source.SMS));
        link(swipe2.transaction(), Source.SMS);

        var email1 = recorder.record(obs(2000, null, T0.plus(Duration.ofMinutes(1)), Source.EMAIL));
        link(email1.transaction(), Source.EMAIL);
        var email2 = recorder.record(obs(2000, null, T0.plus(Duration.ofMinutes(2)), Source.EMAIL));

        assertThat(email1.transaction().getId()).isEqualTo(swipe1.transaction().getId());
        // swipe1 already has an EMAIL, so email2 must go to swipe2 even though swipe1 is closer.
        assertThat(email2.transaction().getId()).isEqualTo(swipe2.transaction().getId());
    }

    @Test
    void differentAccountNeverMatches() {
        var sms = recorder.record(obs(9900, null, T0, Source.SMS));
        link(sms.transaction(), Source.SMS);
        Account other = accounts.findOrCreate("SBI", AccountType.DEBIT_CARD, "1234");

        var email = recorder.record(new Observation(other, 9900, Direction.DEBIT, "X", null, T0, Source.EMAIL, "raw"));

        assertThat(email.created()).isTrue();
    }

    // ---------- helpers ----------

    private Observation obs(long paise, String ref, Instant when, Source source) {
        return new Observation(card, paise, Direction.DEBIT, "SOME MERCHANT", ref, when, source, "raw text");
    }

    /** In production, ingest stores the raw message linked to the transaction; simulate that. */
    private void link(Transaction txn, Source source) {
        jdbc.sql("""
                INSERT INTO raw_messages (source, sender, body, received_at, content_hash, parse_status, transaction_id)
                VALUES (?, 'TEST', 'raw', now(), md5(random()::text) || md5(random()::text), 'PARSED', ?)""")
                .params(source.name(), txn.getId())
                .update();
    }
}
