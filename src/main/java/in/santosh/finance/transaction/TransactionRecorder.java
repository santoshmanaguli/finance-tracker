package in.santosh.finance.transaction;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import in.santosh.finance.account.Account;
import in.santosh.finance.categorization.CategorizationService;
import in.santosh.finance.categorization.MerchantNormalizer;
import in.santosh.finance.common.FinanceProperties;

/**
 * Turns a parsed observation into a golden-record transaction: either matches an existing
 * one (dedup layers 2 and 3) or creates a new one (with a category).
 *
 * Dedup layer 1 (identical message retried) happens earlier, in ingest, on the raw text hash.
 */
@Service
public class TransactionRecorder {

    private final TransactionRepository transactions;
    private final CategorizationService categorization;
    private final Duration window;

    public TransactionRecorder(TransactionRepository transactions, CategorizationService categorization,
                               FinanceProperties properties) {
        this.transactions = transactions;
        this.categorization = categorization;
        this.window = Duration.ofMinutes(properties.dedupWindowMinutes());
    }

    /** What a parser observed. A command object: one argument instead of ten. */
    public record Observation(Account account, long amountPaise, Direction direction, String merchantRaw,
                              String referenceNo, Instant txnTime, Source source, String rawText) {
    }

    public record Outcome(Transaction transaction, boolean created) {
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public Outcome record(Observation obs) {
        Optional<Transaction> existing = findExisting(obs);
        if (existing.isPresent()) {
            return new Outcome(existing.get(), false);
        }

        String merchantNorm = MerchantNormalizer.normalize(obs.merchantRaw());
        var decision = categorization.categorize(merchantNorm);
        var categorySource = switch (decision.source()) {
            case RULE -> CategorySource.RULE;
            case DEFAULT -> CategorySource.DEFAULT;
        };
        var created = new Transaction(obs.account(), obs.amountPaise(), obs.direction(), obs.merchantRaw(),
                merchantNorm, obs.referenceNo(), obs.txnTime(), decision.category(), categorySource,
                obs.source(), obs.rawText());
        return new Outcome(transactions.save(created), true);
    }

    private Optional<Transaction> findExisting(Observation obs) {
        // Layer 2: a reference number is a hard identity.
        if (obs.referenceNo() != null) {
            Optional<Transaction> byRef = transactions.findByAccountIdAndDirectionAndReferenceNo(
                    obs.account().getId(), obs.direction(), obs.referenceNo());
            if (byRef.isPresent()) {
                return byRef;
            }
        }
        // Layer 3: fuzzy match, only against evidence from a DIFFERENT source. Two SMS with the
        // same amount minutes apart are two real payments (an identical SMS was already caught
        // by the hash in layer 1). Candidates never carry a reference (see the query), so a
        // ref-bearing email can still match the ref-less SMS that arrived first.
        Optional<Transaction> fuzzy = transactions.findCrossSourceCandidates(
                        obs.account().getId(), obs.amountPaise(), obs.direction().name(),
                        obs.txnTime().minus(window), obs.txnTime().plus(window), obs.txnTime(),
                        obs.source().name())
                .stream()
                .findFirst();
        // Enrich the golden record: later sources may know the reference the first one lacked.
        fuzzy.ifPresent(t -> t.adoptReference(obs.referenceNo()));
        return fuzzy;
    }
}
