package in.santosh.finance.categorization;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Decides a category for a merchant.
 *
 * Today: first matching rule wins (rules ordered by priority), else "Uncategorized".
 *
 * Extension point for the LLM fallback (later): when no rule matches, call a
 * {@code CategorySuggester} and return {@link Decision.Source#LLM}. Keep it OUT of the
 * ingest transaction (slow network call holding a DB connection) — categorize as
 * Uncategorized first, then enrich asynchronously.
 */
@Service
public class CategorizationService {

    private final MerchantRuleRepository rules;
    private final CategoryRepository categories;

    public CategorizationService(MerchantRuleRepository rules, CategoryRepository categories) {
        this.rules = rules;
        this.categories = categories;
    }

    public record Decision(Category category, Source source) {
        public enum Source { RULE, DEFAULT }
    }

    /**
     * Loads all rules per call. With tens of rules and ~10 SMS/day this is fine and always
     * fresh. If it ever matters: cache the list and evict when a rule is created (Phase 3).
     * Measure first; premature caching brings stale-data bugs.
     */
    @Transactional(readOnly = true)
    public Decision categorize(String normalizedMerchant) {
        if (normalizedMerchant != null) {
            for (MerchantRule rule : rules.findAllInEvaluationOrder()) {
                if (rule.matches(normalizedMerchant)) {
                    return new Decision(rule.getCategory(), Decision.Source.RULE);
                }
            }
        }
        return new Decision(uncategorized(), Decision.Source.DEFAULT);
    }

    private Category uncategorized() {
        // Fail loudly: a missing seed row is a deployment bug, not something to paper over.
        return categories.findByName(Category.UNCATEGORIZED)
                .orElseThrow(() -> new IllegalStateException(
                        "Category '" + Category.UNCATEGORIZED + "' missing: is V2__seed_categories.sql applied?"));
    }
}
