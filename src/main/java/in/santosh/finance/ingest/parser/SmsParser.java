package in.santosh.finance.ingest.parser;

import java.time.Instant;
import java.util.Set;

/**
 * Strategy pattern: one implementation per bank. Adding a bank = adding one {@code @Component}
 * class; no existing code changes (the Open/Closed Principle). Spring discovers every
 * implementation and {@link SmsParserRegistry} picks the right one by sender.
 *
 * Implementations must be stateless and thread-safe (one instance serves all requests):
 * compile regex {@code Pattern}s once as static finals; never store per-message state in fields.
 */
public interface SmsParser {

    /**
     * Normalized sender codes this parser handles, e.g. {@code Set.of("HDFCBK")}.
     * See {@link SenderIds#normalize(String)}: "AX-HDFCBK-S" becomes "HDFCBK".
     */
    Set<String> senderCodes();

    /**
     * @param body       the SMS text, unmodified
     * @param receivedAt when the phone received it; use it to fill in a missing year or time
     */
    ParseResult parse(String body, Instant receivedAt);
}
