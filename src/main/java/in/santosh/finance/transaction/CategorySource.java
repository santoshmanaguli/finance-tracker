package in.santosh.finance.transaction;

/**
 * Who decided a transaction's category. Precedence when re-categorizing in bulk:
 * USER is never overwritten; RULE/LLM/DEFAULT may be recomputed.
 */
public enum CategorySource {
    DEFAULT,
    RULE,
    USER,
    LLM
}
