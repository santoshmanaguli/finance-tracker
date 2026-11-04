package in.santosh.finance.ingest.parser;

/**
 * The three possible outcomes of parsing, as a sealed type (Java 17+).
 *
 * Why not return null / throw? A sealed interface makes every outcome explicit, and a
 * {@code switch} over it is checked by the compiler: add a fourth outcome and every switch
 * that doesn't handle it fails to compile. Exceptions are for the unexpected; "this SMS is a
 * promo" is expected.
 */
public sealed interface ParseResult {

    /** A real transaction was found. */
    record Parsed(ParsedTransaction transaction) implements ParseResult {
    }

    /** Recognized and deliberately skipped (OTP, promo, balance enquiry, payment reminder...). */
    record Ignored(String reason) implements ParseResult {
    }

    /** Looked relevant but could not be parsed. Goes to the unparsed inbox for review. */
    record Failed(String reason) implements ParseResult {
    }
}
