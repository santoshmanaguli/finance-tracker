package in.santosh.finance.ingest;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.simple.JdbcClient;

import in.santosh.finance.IntegrationTest;
import in.santosh.finance.TestData;

/**
 * End to end over real HTTP: filter -> controller -> service -> parser -> dedup -> Postgres.
 * These requests commit, so each test starts from clean tables.
 */
@IntegrationTest
class SmsIngestIntegrationTest {

    static final String SWIGGY_SMS = "Rs 1,234.50 debited from Card XX1234 at SWIGGY on 04-11-2026 10:15";

    @LocalServerPort
    int port;

    @Autowired
    JdbcClient jdbc;

    private final HttpClient http = HttpClient.newHttpClient();

    @BeforeEach
    void cleanDatabase() {
        TestData.resetIngestTables(jdbc);
        TestData.ensureCategory(jdbc, "Uncategorized");
        TestData.ensureRule(jdbc, "SWIGGY", "CONTAINS", "Food", 100);
    }

    // ---------- security ----------

    @Test
    void rejectsMissingToken() throws Exception {
        assertThat(post(json("AX-FAKEBK", SWIGGY_SMS), null).statusCode()).isEqualTo(401);
        assertThat(count("raw_messages")).isZero();
    }

    @Test
    void rejectsWrongToken() throws Exception {
        assertThat(post(json("AX-FAKEBK", SWIGGY_SMS), "wrong").statusCode()).isEqualTo(401);
    }

    @Test
    void rejectsInvalidPayloadWithProblemDetail() throws Exception {
        var response = post("""
                {"sender": "", "body": "x"}""", IntegrationTest.TOKEN);

        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(response.headers().firstValue("Content-Type")).hasValueSatisfying(
                ct -> assertThat(ct).contains("application/problem+json"));
    }

    // ---------- happy path ----------

    @Test
    void createsCategorizedTransaction() throws Exception {
        var response = ingest("AX-FAKEBK", SWIGGY_SMS);

        assertThat(response.statusCode()).isEqualTo(201);
        assertThat(response.body()).contains("\"status\":\"CREATED\"");

        Map<String, Object> row = jdbc.sql("""
                SELECT t.amount_paise, t.direction, t.merchant_norm, t.category_source, c.name AS category,
                       a.institution, a.last4, t.txn_time = TIMESTAMPTZ '2026-11-04 10:15+05:30' AS time_ok
                FROM transactions t
                JOIN categories c ON c.id = t.category_id
                JOIN accounts a ON a.id = t.account_id""").query().singleRow();
        assertThat(row).containsEntry("amount_paise", 123450L)
                .containsEntry("direction", "DEBIT")
                .containsEntry("merchant_norm", "SWIGGY")
                .containsEntry("category", "Food")
                .containsEntry("category_source", "RULE")
                .containsEntry("institution", "FAKEBANK")
                .containsEntry("last4", "1234")
                .containsEntry("time_ok", true);
        assertThat(statusOfOnlyRawMessage()).isEqualTo("PARSED");
    }

    @Test
    void unknownMerchantIsUncategorized() throws Exception {
        ingest("AX-FAKEBK", "Rs 99 debited from Card XX1234 at RANDOM SHOP on 04-11-2026 11:00");

        assertThat(jdbc.sql("""
                SELECT c.name FROM transactions t JOIN categories c ON c.id = t.category_id""")
                .query(String.class).single()).isEqualTo("Uncategorized");
    }

    // ---------- dedup layer 1 ----------

    @Test
    void retriedMessageIsIdempotent() throws Exception {
        ingest("AX-FAKEBK", SWIGGY_SMS);
        var retry = ingest("JD-FAKEBK", SWIGGY_SMS);   // different operator route, same SMS

        assertThat(retry.statusCode()).isEqualTo(200);
        assertThat(retry.body()).contains("\"status\":\"ALREADY_RECEIVED\"");
        assertThat(count("transactions")).isEqualTo(1);
        assertThat(count("raw_messages")).isEqualTo(1);
    }

    @Test
    void twoGenuinePaymentsOfSameAmountAreBothKept() throws Exception {
        ingest("AX-FAKEBK", "Rs 20 debited from Card XX1234 at CHAI POINT on 04-11-2026 10:00");
        ingest("AX-FAKEBK", "Rs 20 debited from Card XX1234 at CHAI POINT on 04-11-2026 10:05");

        assertThat(count("transactions")).isEqualTo(2);
    }

    // ---------- never drop a message ----------

    @Test
    void unknownSenderGoesToUnparsedInbox() throws Exception {
        var response = ingest("AX-NEWBNK", "Rs 500 spent on your card");

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).contains("\"status\":\"UNPARSED\"");
        assertThat(jdbc.sql("SELECT body FROM unparsed_messages").query(String.class).single())
                .isEqualTo("Rs 500 spent on your card");
    }

    @Test
    void parserCrashStillStoresMessage() throws Exception {
        var response = ingest("AX-BOOMBK", "anything");

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(jdbc.sql("SELECT parse_error FROM unparsed_messages").query(String.class).single())
                .contains("simulated parser bug");
    }

    @Test
    void unrecognizedFormatFromKnownBankIsUnparsed() throws Exception {
        ingest("AX-FAKEBK", "Your card XX1234 statement is ready");

        assertThat(statusOfOnlyRawMessage()).isEqualTo("UNPARSED");
    }

    @Test
    void otpIsIgnoredAndNotStoredInClear() throws Exception {
        var response = ingest("AX-FAKEBK", "482913 is your OTP for Rs 500 at AMAZON. Do not share.");

        assertThat(response.body()).contains("\"status\":\"IGNORED\"");
        assertThat(jdbc.sql("SELECT body FROM raw_messages").query(String.class).single())
                .doesNotContain("482913");
    }

    // ---------- helpers ----------

    private HttpResponse<String> ingest(String sender, String body) throws Exception {
        return post(json(sender, body), IntegrationTest.TOKEN);
    }

    private static String json(String sender, String body) {
        return """
                {"sender": "%s", "body": "%s", "receivedAt": "2026-11-04T10:16:00+05:30"}"""
                .formatted(sender, body);
    }

    private HttpResponse<String> post(String json, String token) throws Exception {
        var builder = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/ingest/sms"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json));
        if (token != null) {
            builder.header("X-Api-Token", token);
        }
        return http.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    private int count(String table) {
        return jdbc.sql("SELECT count(*) FROM " + table).query(Integer.class).single();
    }

    private String statusOfOnlyRawMessage() {
        return jdbc.sql("SELECT parse_status FROM raw_messages").query(String.class).single();
    }
}
