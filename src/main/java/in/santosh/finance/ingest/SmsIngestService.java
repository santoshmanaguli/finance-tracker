package in.santosh.finance.ingest;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import in.santosh.finance.account.AccountService;
import in.santosh.finance.ingest.parser.ParseResult;
import in.santosh.finance.ingest.parser.ParsedTransaction;
import in.santosh.finance.ingest.parser.SmsParserRegistry;
import in.santosh.finance.transaction.Source;
import in.santosh.finance.transaction.TransactionRecorder;

/**
 * The ingest use case, in order:
 * <ol>
 *   <li>Layer 1 dedup: seen this exact content before? Return the earlier result (idempotent).</li>
 *   <li>Parse. Every outcome is stored; nothing is dropped.</li>
 *   <li>For a parsed transaction: resolve the account, then layer 2/3 dedup or create.</li>
 * </ol>
 * All of it in ONE database transaction: the raw message, a new account and a new
 * transaction commit together or not at all. No half-written state.
 */
@Service
public class SmsIngestService {

    private static final Logger log = LoggerFactory.getLogger(SmsIngestService.class);
    private static final Source SOURCE = Source.SMS;

    private final RawMessageRepository rawMessages;
    private final SmsParserRegistry parsers;
    private final AccountService accounts;
    private final TransactionRecorder recorder;
    private final Clock clock;

    public SmsIngestService(RawMessageRepository rawMessages, SmsParserRegistry parsers, AccountService accounts,
                            TransactionRecorder recorder, Clock clock) {
        this.rawMessages = rawMessages;
        this.parsers = parsers;
        this.accounts = accounts;
        this.recorder = recorder;
        this.clock = clock;
    }

    public record Command(String sender, String body, Instant receivedAt) {
    }

    public enum Status {
        /** New transaction recorded. */
        CREATED,
        /** Parsed, and matched a transaction we already had from another source. */
        MATCHED_EXISTING,
        /** This exact message was already received (e.g. Shortcut fired twice). No-op. */
        ALREADY_RECEIVED,
        /** Deliberately skipped (OTP, promo). */
        IGNORED,
        /** Stored in the unparsed inbox for review. */
        UNPARSED
    }

    public record Result(Status status, long rawMessageId, Long transactionId) {
    }

    @Transactional
    public Result ingest(Command cmd) {
        Instant receivedAt = cmd.receivedAt() != null ? cmd.receivedAt() : clock.instant();
        String hash = ContentHasher.hash(SOURCE, cmd.sender(), cmd.body());

        Optional<RawMessage> seen = rawMessages.findByContentHash(hash);
        if (seen.isPresent()) {
            RawMessage earlier = seen.get();
            log.info("Raw message {} received again; ignoring retry", earlier.getId());
            Long txnId = earlier.getTransaction() != null ? earlier.getTransaction().getId() : null;
            return new Result(Status.ALREADY_RECEIVED, earlier.getId(), txnId);
        }

        ParseResult parsed = parsers.parse(cmd.sender(), cmd.body(), receivedAt);

        // Exhaustive switch over a sealed type: no default branch needed, and adding a new
        // ParseResult variant is a compile error here until it is handled.
        Result result = switch (parsed) {
            case ParseResult.Parsed p -> record(cmd, receivedAt, hash, p.transaction());
            case ParseResult.Ignored i -> {
                // Don't keep OTP text at rest: it's sensitive and has no value to us.
                String stored = SmsParserRegistry.isOtp(cmd.body()) ? "[redacted: OTP]" : cmd.body();
                var raw = rawMessages.save(RawMessage.ignored(SOURCE, cmd.sender(), stored, receivedAt, hash, i.reason()));
                yield new Result(Status.IGNORED, raw.getId(), null);
            }
            case ParseResult.Failed f -> {
                var raw = rawMessages.save(RawMessage.unparsed(SOURCE, cmd.sender(), cmd.body(), receivedAt, hash, f.reason()));
                yield new Result(Status.UNPARSED, raw.getId(), null);
            }
        };
        // Never log the SMS body: it is financial data. Ids are enough to investigate.
        log.info("Ingested SMS from {}: status={} raw={} txn={}",
                cmd.sender(), result.status(), result.rawMessageId(), result.transactionId());
        return result;
    }

    private Result record(Command cmd, Instant receivedAt, String hash, ParsedTransaction p) {
        var account = accounts.findOrCreate(p.institution(), p.accountType(), p.last4());
        Instant txnTime = p.txnTime() != null ? p.txnTime() : receivedAt;
        var outcome = recorder.record(new TransactionRecorder.Observation(
                account, p.amountPaise(), p.direction(), p.merchantRaw(), p.referenceNo(), txnTime,
                SOURCE, cmd.body()));
        var raw = rawMessages.save(RawMessage.linked(SOURCE, cmd.sender(), cmd.body(), receivedAt, hash,
                outcome.transaction(), outcome.created()));
        return new Result(outcome.created() ? Status.CREATED : Status.MATCHED_EXISTING,
                raw.getId(), outcome.transaction().getId());
    }
}
