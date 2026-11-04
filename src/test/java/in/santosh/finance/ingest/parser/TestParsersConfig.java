package in.santosh.finance.ingest.parser;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

import in.santosh.finance.account.AccountType;
import in.santosh.finance.common.TimeConfig;
import in.santosh.finance.transaction.Direction;

/**
 * Test-only parsers for a fictional bank, so the whole pipeline (HTTP -> parse -> dedup ->
 * categorize -> DB) can be tested without guessing real bank formats.
 *
 * The real HDFC / SBI / OneCard parsers will be built from your masked samples.
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestParsersConfig {

    /** Format: "Rs 1,234.50 debited from Card XX1234 at SWIGGY on 04-11-2026 10:15. Ref 123456789012" */
    public static final class FakeBankParser implements SmsParser {

        private static final Pattern TXN = Pattern.compile(
                "Rs\\.?\\s*(?<amount>[\\d,]+(?:\\.\\d{1,2})?)\\s+(?<dir>debited|credited)\\s+(?:from|to)\\s+Card\\s+XX(?<last4>\\d{4})"
                        + "(?:\\s+at\\s+(?<merchant>.+?))?\\s+on\\s+(?<when>\\d{2}-\\d{2}-\\d{4} \\d{2}:\\d{2})"
                        + "(?:\\.\\s*Ref\\s+(?<ref>\\w+))?",
                Pattern.CASE_INSENSITIVE);
        private static final DateTimeFormatter WHEN = DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm");

        @Override
        public Set<String> senderCodes() {
            return Set.of("FAKEBK");
        }

        @Override
        public ParseResult parse(String body, Instant receivedAt) {
            if (body.toLowerCase().contains("offer")) {
                return new ParseResult.Ignored("promotional");
            }
            Matcher m = TXN.matcher(body);
            if (!m.find()) {
                return new ParseResult.Failed("FakeBank: no transaction pattern matched");
            }
            // Bank SMS times are IST wall-clock times; convert to an absolute instant.
            Instant when = LocalDateTime.parse(m.group("when"), WHEN).atZone(TimeConfig.IST).toInstant();
            return new ParseResult.Parsed(new ParsedTransaction(
                    "FAKEBANK", AccountType.CREDIT_CARD, m.group("last4"),
                    Amounts.toPaise(m.group("amount")),
                    m.group("dir").equalsIgnoreCase("debited") ? Direction.DEBIT : Direction.CREDIT,
                    m.group("merchant"), m.group("ref"), when));
        }
    }

    /** Always throws: proves a buggy parser cannot lose a message. */
    public static final class ExplodingParser implements SmsParser {
        @Override
        public Set<String> senderCodes() {
            return Set.of("BOOMBK");
        }

        @Override
        public ParseResult parse(String body, Instant receivedAt) {
            throw new IllegalStateException("simulated parser bug");
        }
    }

    @Bean
    FakeBankParser fakeBankParser() {
        return new FakeBankParser();
    }

    @Bean
    ExplodingParser explodingParser() {
        return new ExplodingParser();
    }
}
