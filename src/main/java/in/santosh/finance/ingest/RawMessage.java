package in.santosh.finance.ingest;

import java.time.Instant;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import in.santosh.finance.transaction.Source;
import in.santosh.finance.transaction.Transaction;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/** One inbound message: the evidence. Transactions are derived from these. */
@Entity
@Table(name = "raw_messages")
public class RawMessage {

    public enum ParseStatus {
        /** Produced a new transaction. */
        PARSED,
        /** Could not be parsed: shows up in the unparsed inbox. */
        UNPARSED,
        /** Deliberately skipped (OTP, promo...). */
        IGNORED,
        /** Parsed fine, but matched a transaction that already existed (another source). */
        DUPLICATE
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private Source source;

    @Column(nullable = false, length = 64)
    private String sender;

    @Column(nullable = false, columnDefinition = "text")
    private String body;

    @Column(name = "received_at", nullable = false)
    private Instant receivedAt;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "content_hash", nullable = false, length = 64)
    private String contentHash;

    @Enumerated(EnumType.STRING)
    @Column(name = "parse_status", nullable = false, length = 16)
    private ParseStatus parseStatus;

    @Column(name = "parse_error", columnDefinition = "text")
    private String parseError;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "transaction_id")
    private Transaction transaction;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected RawMessage() {
    }

    private RawMessage(Source source, String sender, String body, Instant receivedAt, String contentHash,
                       ParseStatus status, String parseError, Transaction transaction) {
        this.source = source;
        this.sender = sender;
        this.body = body;
        this.receivedAt = receivedAt;
        this.contentHash = contentHash;
        this.parseStatus = status;
        this.parseError = parseError;
        this.transaction = transaction;
    }

    // Static factories: each legal state has a named constructor, so an UNPARSED message with a
    // transaction (or a PARSED one without) can't be created by accident.

    static RawMessage linked(Source source, String sender, String body, Instant receivedAt, String hash,
                             Transaction transaction, boolean createdNew) {
        return new RawMessage(source, sender, body, receivedAt, hash,
                createdNew ? ParseStatus.PARSED : ParseStatus.DUPLICATE, null, transaction);
    }

    static RawMessage unparsed(Source source, String sender, String body, Instant receivedAt, String hash,
                               String reason) {
        return new RawMessage(source, sender, body, receivedAt, hash, ParseStatus.UNPARSED, reason, null);
    }

    static RawMessage ignored(Source source, String sender, String storedBody, Instant receivedAt, String hash,
                              String reason) {
        return new RawMessage(source, sender, storedBody, receivedAt, hash, ParseStatus.IGNORED, reason, null);
    }

    public Long getId() {
        return id;
    }

    public Source getSource() {
        return source;
    }

    public String getSender() {
        return sender;
    }

    public String getBody() {
        return body;
    }

    public Instant getReceivedAt() {
        return receivedAt;
    }

    public ParseStatus getParseStatus() {
        return parseStatus;
    }

    public String getParseError() {
        return parseError;
    }

    public Transaction getTransaction() {
        return transaction;
    }
}
