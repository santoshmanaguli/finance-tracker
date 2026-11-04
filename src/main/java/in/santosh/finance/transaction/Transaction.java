package in.santosh.finance.transaction;

import java.time.Instant;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import in.santosh.finance.account.Account;
import in.santosh.finance.categorization.Category;
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
import jakarta.persistence.Version;

/**
 * The golden record: one row per real-world transaction.
 *
 * No public setters. State changes go through intention-revealing methods
 * ({@link #recategorize}) so business rules live with the data they protect.
 */
@Entity
@Table(name = "transactions")
public class Transaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "account_id", nullable = false)
    private Account account;

    @Column(name = "amount_paise", nullable = false)
    private long amountPaise;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 6)
    private Direction direction;

    @Column(name = "merchant_raw", length = 256)
    private String merchantRaw;

    @Column(name = "merchant_norm", length = 256)
    private String merchantNorm;

    @Column(name = "reference_no", length = 64)
    private String referenceNo;

    @Column(name = "txn_time", nullable = false)
    private Instant txnTime;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "category_id", nullable = false)
    private Category category;

    @Enumerated(EnumType.STRING)
    @Column(name = "category_source", nullable = false, length = 16)
    private CategorySource categorySource;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private Source source;

    @Column(name = "raw_text", nullable = false, columnDefinition = "text")
    private String rawText;

    /**
     * Optimistic locking: UPDATE ... WHERE id = ? AND version = ?. If someone else changed the
     * row since we read it, zero rows match and Hibernate throws instead of silently overwriting.
     */
    @Version
    @Column(nullable = false)
    private int version;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Transaction() {
    }

    Transaction(Account account, long amountPaise, Direction direction, String merchantRaw, String merchantNorm,
                String referenceNo, Instant txnTime, Category category, CategorySource categorySource,
                Source source, String rawText) {
        if (amountPaise <= 0) {
            throw new IllegalArgumentException("amountPaise must be positive; direction carries the sign");
        }
        this.account = account;
        this.amountPaise = amountPaise;
        this.direction = direction;
        this.merchantRaw = merchantRaw;
        this.merchantNorm = merchantNorm;
        this.referenceNo = referenceNo;
        this.txnTime = txnTime;
        this.category = category;
        this.categorySource = categorySource;
        this.source = source;
        this.rawText = rawText;
    }

    /** Fill in a reference number learned from a later source. Never overwrites an existing one. */
    void adoptReference(String referenceNo) {
        if (this.referenceNo == null && referenceNo != null) {
            this.referenceNo = referenceNo;
        }
    }

    /** Phase 3 will use this. A user decision always wins over rules. */
    public void recategorize(Category newCategory, CategorySource decidedBy) {
        if (this.categorySource == CategorySource.USER && decidedBy != CategorySource.USER) {
            return;
        }
        this.category = newCategory;
        this.categorySource = decidedBy;
    }

    public Long getId() {
        return id;
    }

    public Account getAccount() {
        return account;
    }

    public long getAmountPaise() {
        return amountPaise;
    }

    public Direction getDirection() {
        return direction;
    }

    public String getMerchantRaw() {
        return merchantRaw;
    }

    public String getMerchantNorm() {
        return merchantNorm;
    }

    public String getReferenceNo() {
        return referenceNo;
    }

    public Instant getTxnTime() {
        return txnTime;
    }

    public Category getCategory() {
        return category;
    }

    public CategorySource getCategorySource() {
        return categorySource;
    }

    public Source getSource() {
        return source;
    }

    public String getRawText() {
        return rawText;
    }
}
