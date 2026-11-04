package in.santosh.finance.ingest.parser;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;

import in.santosh.finance.ingest.parser.TestParsersConfig.ExplodingParser;
import in.santosh.finance.ingest.parser.TestParsersConfig.FakeBankParser;

/** Plain unit test: no Spring, no DB. Runs in milliseconds. */
class SmsParserRegistryTest {

    private final SmsParserRegistry registry =
            new SmsParserRegistry(List.of(new FakeBankParser(), new ExplodingParser()));
    private final Instant now = Instant.parse("2026-11-04T05:00:00Z");

    @Test
    void routesBySenderHeaderRegardlessOfOperatorPrefix() {
        var result = registry.parse("JD-FAKEBK-S",
                "Rs 250 debited from Card XX1234 at ZOMATO on 04-11-2026 10:15", now);

        assertThat(result).isInstanceOf(ParseResult.Parsed.class);
        var txn = ((ParseResult.Parsed) result).transaction();
        assertThat(txn.amountPaise()).isEqualTo(25000);
        assertThat(txn.last4()).isEqualTo("1234");
        // 10:15 IST = 04:45 UTC
        assertThat(txn.txnTime()).isEqualTo(Instant.parse("2026-11-04T04:45:00Z"));
    }

    @Test
    void unknownSenderIsFailedNotDropped() {
        assertThat(registry.parse("AX-NOPEBK", "anything", now))
                .isEqualTo(new ParseResult.Failed("No parser for sender code NOPEBK"));
    }

    @Test
    void otpIsIgnoredBeforeAnyParserRuns() {
        assertThat(registry.parse("AX-BOOMBK", "123456 is your OTP for txn of Rs 500", now))
                .isEqualTo(new ParseResult.Ignored("OTP"));
    }

    @Test
    void parserExceptionBecomesFailedResult() {
        assertThat(registry.parse("AX-BOOMBK", "boom", now))
                .isInstanceOf(ParseResult.Failed.class)
                .asString().contains("simulated parser bug");
    }

    @Test
    void twoParsersClaimingSameSenderFailsAtStartup() {
        assertThatThrownBy(() -> new SmsParserRegistry(List.of(new FakeBankParser(), new FakeBankParser())))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("FAKEBK");
    }
}
