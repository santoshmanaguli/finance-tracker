package in.santosh.finance.account;

import java.time.Instant;
import java.util.regex.Pattern;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * A bank account or card, identified only by institution + type + last 4 digits.
 *
 * JPA entities need a no-arg constructor (Hibernate instantiates them via reflection);
 * it is {@code protected} so application code must use the validating constructor.
 */
@Entity
@Table(name = "accounts")
public class Account {

    private static final Pattern LAST4 = Pattern.compile("\\d{4}");

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 32)
    private String institution;

    @Enumerated(EnumType.STRING)
    @Column(name = "account_type", nullable = false, length = 16)
    private AccountType accountType;

    // CHAR(4) in the schema; Hibernate assumes VARCHAR for String unless told otherwise.
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(nullable = false, length = 4)
    private String last4;

    @Column(length = 64)
    private String nickname;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected Account() {
    }

    public Account(String institution, AccountType accountType, String last4) {
        if (last4 == null || !LAST4.matcher(last4).matches()) {
            // Defence in depth: the DB has the same CHECK, but failing here gives a clear error
            // and guarantees we never even attempt to persist a full card number.
            throw new IllegalArgumentException("last4 must be exactly 4 digits");
        }
        this.institution = institution;
        this.accountType = accountType;
        this.last4 = last4;
    }

    public Long getId() {
        return id;
    }

    public String getInstitution() {
        return institution;
    }

    public AccountType getAccountType() {
        return accountType;
    }

    public String getLast4() {
        return last4;
    }

    public String getNickname() {
        return nickname;
    }
}
