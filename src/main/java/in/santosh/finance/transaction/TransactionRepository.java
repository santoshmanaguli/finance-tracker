package in.santosh.finance.transaction;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TransactionRepository extends JpaRepository<Transaction, Long> {

    /** Dedup layer 2. Served by the partial unique index ux_transactions_account_dir_ref. */
    Optional<Transaction> findByAccountIdAndDirectionAndReferenceNo(Long accountId, Direction direction, String referenceNo);

    /**
     * Dedup layer 3 candidates: same account, amount and direction, within the time window,
     * and NOT already backed by evidence from the incoming source.
     *
     * Why the NOT EXISTS? Two real Rs 20 swipes at 10:00 and 10:05 (two SMS, two rows).
     * Later the two emails arrive. Email #1 links to the 10:00 row. Email #2 must NOT link to
     * the 10:00 row again (it already has an EMAIL), so it falls through to the 10:05 row.
     *
     * Rows that already carry a reference number are excluded: if they were the same
     * transaction, the exact reference lookup (layer 2) would have found them; a row with a
     * DIFFERENT reference is by definition a different transaction.
     *
     * Native SQL: raw_messages belongs to the ingest module, and this keeps the transaction
     * module free of a compile-time dependency on it. Uses ix_transactions_account_amount_time.
     * Closest in time wins; id breaks ties deterministically.
     */
    @Query(nativeQuery = true, value = """
            SELECT t.* FROM transactions t
            WHERE t.account_id = :accountId
              AND t.amount_paise = :amountPaise
              AND t.direction = :direction
              AND t.txn_time BETWEEN :from AND :to
              AND t.reference_no IS NULL
              AND NOT EXISTS (SELECT 1 FROM raw_messages r
                              WHERE r.transaction_id = t.id AND r.source = :source)
            ORDER BY abs(extract(epoch FROM (t.txn_time - CAST(:around AS timestamptz)))), t.id
            """)
    List<Transaction> findCrossSourceCandidates(@Param("accountId") Long accountId,
                                                @Param("amountPaise") long amountPaise,
                                                @Param("direction") String direction,
                                                @Param("from") Instant from,
                                                @Param("to") Instant to,
                                                @Param("around") Instant around,
                                                @Param("source") String source);
}
