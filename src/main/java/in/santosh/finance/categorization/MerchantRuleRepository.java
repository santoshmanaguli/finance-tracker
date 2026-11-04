package in.santosh.finance.categorization;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface MerchantRuleRepository extends JpaRepository<MerchantRule, Long> {

    /**
     * All rules in evaluation order, with their category fetched in the SAME query.
     *
     * Without {@code JOIN FETCH}, reading rule.getCategory() for N rules would fire N extra
     * SELECTs: the "N+1 query problem", one of the most common ORM performance bugs.
     * The {@code id} tiebreaker makes ordering deterministic when priorities are equal.
     */
    @Query("SELECT r FROM MerchantRule r JOIN FETCH r.category ORDER BY r.priority ASC, r.id ASC")
    List<MerchantRule> findAllInEvaluationOrder();
}
