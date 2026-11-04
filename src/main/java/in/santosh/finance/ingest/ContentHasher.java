package in.santosh.finance.ingest;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

import in.santosh.finance.ingest.parser.SenderIds;
import in.santosh.finance.transaction.Source;

/**
 * Dedup layer 1: a fingerprint of the message CONTENT, used as an idempotency key.
 *
 * Deliberately excluded: the received timestamp (a retried Shortcut call carries a new one)
 * and the operator prefix of the sender (AX- vs JD- is routing, not content).
 * The newline separator prevents ambiguity: ("AB","C") and ("A","BC") hash differently.
 */
final class ContentHasher {

    private ContentHasher() {
    }

    static String hash(Source source, String sender, String body) {
        String canonical = source.name() + '\n' + SenderIds.normalize(sender) + '\n' + body.strip();
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(canonical.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is guaranteed by the JDK spec", e);
        }
    }
}
