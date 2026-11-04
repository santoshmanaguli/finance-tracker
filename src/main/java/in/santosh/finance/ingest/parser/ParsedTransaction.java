package in.santosh.finance.ingest.parser;

import java.time.Instant;
import java.util.Objects;

import in.santosh.finance.account.AccountType;
import in.santosh.finance.transaction.Direction;

/**
 * What a parser extracted from one message. Immutable value object.
 *
 * @param institution  e.g. "HDFC" (the parser knows its own bank)
 * @param accountType  card vs savings account, as stated in the SMS
 * @param last4        last 4 digits only. Parsers must never pass more.
 * @param amountPaise  positive; {@code direction} carries the sign
 * @param merchantRaw  payee / merchant as written, or null if the SMS has none
 * @param referenceNo  UPI ref / txn id, normalized (digits/letters only), or null
 * @param txnTime      time stated in the SMS, or null to fall back to the received time
 */
public record ParsedTransaction(String institution, AccountType accountType, String last4, long amountPaise,
                                Direction direction, String merchantRaw, String referenceNo, Instant txnTime) {

    public ParsedTransaction {
        Objects.requireNonNull(institution, "institution");
        Objects.requireNonNull(accountType, "accountType");
        Objects.requireNonNull(direction, "direction");
        if (last4 == null || !last4.matches("\\d{4}")) {
            throw new IllegalArgumentException("last4 must be exactly 4 digits");
        }
        if (amountPaise <= 0) {
            throw new IllegalArgumentException("amountPaise must be positive");
        }
    }
}
