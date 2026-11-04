package in.santosh.finance.account;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Spring Data derives the SQL from the method name:
 * {@code WHERE institution = ? AND account_type = ? AND last4 = ?}
 * which is exactly the unique key ux_accounts_identity, so it is an index lookup.
 */
public interface AccountRepository extends JpaRepository<Account, Long> {

    Optional<Account> findByInstitutionAndAccountTypeAndLast4(String institution, AccountType accountType, String last4);
}
