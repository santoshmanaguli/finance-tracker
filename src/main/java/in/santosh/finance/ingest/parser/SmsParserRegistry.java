package in.santosh.finance.ingest.parser;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Routes a message to the parser for its sender, and guarantees the "never drop a message"
 * rule: whatever happens inside a parser, the caller gets a {@link ParseResult}.
 */
@Component
public class SmsParserRegistry {

    private static final Logger log = LoggerFactory.getLogger(SmsParserRegistry.class);

    /** OTPs are never useful here and are sensitive. Checked before any bank parser runs. */
    private static final Pattern OTP = Pattern.compile("\\bOTP\\b|one[- ]time password", Pattern.CASE_INSENSITIVE);

    private final Map<String, SmsParser> bySenderCode = new HashMap<>();

    /** Spring injects every SmsParser bean. Zero is valid (everything goes to the inbox). */
    public SmsParserRegistry(List<SmsParser> parsers) {
        for (SmsParser parser : parsers) {
            for (String code : parser.senderCodes()) {
                SmsParser previous = bySenderCode.put(code, parser);
                if (previous != null) {
                    // Fail at startup, not on the first real SMS in production.
                    throw new IllegalStateException("Sender code " + code + " claimed by both "
                            + previous.getClass().getSimpleName() + " and " + parser.getClass().getSimpleName());
                }
            }
        }
        log.info("SMS parsers registered for sender codes: {}", bySenderCode.keySet());
    }

    public static boolean isOtp(String body) {
        return OTP.matcher(body).find();
    }

    public ParseResult parse(String sender, String body, Instant receivedAt) {
        if (isOtp(body)) {
            return new ParseResult.Ignored("OTP");
        }
        String code = SenderIds.normalize(sender);
        SmsParser parser = bySenderCode.get(code);
        if (parser == null) {
            return new ParseResult.Failed("No parser for sender code " + code);
        }
        try {
            return parser.parse(body, receivedAt);
        } catch (RuntimeException e) {
            // A bug in one parser must not lose the message or fail the request.
            log.warn("Parser {} threw for sender {}", parser.getClass().getSimpleName(), code, e);
            return new ParseResult.Failed(parser.getClass().getSimpleName() + " error: " + e.getMessage());
        }
    }
}
