package in.santosh.finance.account;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AccountService {

    private final AccountRepository accounts;

    public AccountService(AccountRepository accounts) {
        this.accounts = accounts;
    }

    /**
     * Accounts are discovered from messages: the first SMS mentioning "HDFC credit card XX1234"
     * creates that account. No manual setup needed.
     *
     * MANDATORY: must be called inside the caller's transaction, so the account and the
     * transaction that references it commit (or roll back) together.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public Account findOrCreate(String institution, AccountType type, String last4) {
        return accounts.findByInstitutionAndAccountTypeAndLast4(institution, type, last4)
                .orElseGet(() -> accounts.save(new Account(institution, type, last4)));
    }
}
