package in.santosh.finance.categorization;

import java.time.Instant;

import org.hibernate.annotations.CreationTimestamp;

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

@Entity
@Table(name = "merchant_rules")
public class MerchantRule {

    public enum MatchType { EXACT, CONTAINS }

    public enum CreatedBy { SYSTEM, USER }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 128)
    private String pattern;

    @Enumerated(EnumType.STRING)
    @Column(name = "match_type", nullable = false, length = 16)
    private MatchType matchType;

    // LAZY: don't load the category unless someone asks for it.
    // (@ManyToOne defaults to EAGER, a classic source of hidden extra queries.)
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "category_id", nullable = false)
    private Category category;

    @Column(nullable = false)
    private int priority;

    @Enumerated(EnumType.STRING)
    @Column(name = "created_by", nullable = false, length = 16)
    private CreatedBy createdBy;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected MerchantRule() {
    }

    public MerchantRule(String pattern, MatchType matchType, Category category, int priority, CreatedBy createdBy) {
        this.pattern = pattern.toUpperCase();
        this.matchType = matchType;
        this.category = category;
        this.priority = priority;
        this.createdBy = createdBy;
    }

    /** {@code normalizedMerchant} must already be normalized (see {@link MerchantNormalizer}). */
    boolean matches(String normalizedMerchant) {
        return switch (matchType) {
            case EXACT -> normalizedMerchant.equals(pattern);
            case CONTAINS -> normalizedMerchant.contains(pattern);
        };
    }

    public Long getId() {
        return id;
    }

    public String getPattern() {
        return pattern;
    }

    public MatchType getMatchType() {
        return matchType;
    }

    public Category getCategory() {
        return category;
    }

    public int getPriority() {
        return priority;
    }

    public CreatedBy getCreatedBy() {
        return createdBy;
    }
}
