# ADR-0005: Three-layer deduplication

- **Status:** Accepted
- **Date:** 2026-11-03 (layer 3 refined 2026-11-04)

## Context

The same payment can arrive as an SMS, an email and a statement line, and the same SMS can arrive twice when a Shortcut retries or two keyword automations fire. Yet two *genuine* identical payments (two ₹20 chai payments minutes apart) must both be kept.

## Decision

| Layer | Catches | Mechanism | Where enforced |
|---|---|---|---|
| 1 | Same message twice | SHA-256 of `source + sender header + body`; the timestamp is excluded | DB unique constraint (race-safe) |
| 2 | Same payment, reference number known | Exact `(account, direction, reference_no)` match | DB partial unique index |
| 3 | Same payment, no reference | Same account, amount and direction, within ±15 min, **only from a different source**, only against rows without a reference; closest in time wins, and each source pairs at most once | Application code (a heuristic) |

When a layer-3 match happens and the newer message carries a reference, the golden record **adopts** it.

## Principle

**Constraints where the rule is absolute; code plus human review where it's a heuristic.** Silently merging two real payments is worse than showing a duplicate, so statement matches (Phase 5) will be *flagged* for review, not auto-merged.

## Consequences

- ➕ Retries and parallel automations are harmless (idempotency).
- ➕ Concurrent duplicates are caught by the database. The loser gets 409, and no data is lost.
- ➖ Layer 3 can still mis-pair in rare edge cases, such as two identical swipes from two sources with skewed clocks. Reconciliation and the UI are the safety net.
- Tested in `TransactionRecorderIntegrationTest` and `SmsIngestIntegrationTest`.
