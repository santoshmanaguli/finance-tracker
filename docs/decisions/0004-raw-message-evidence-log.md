# ADR-0004: Store every inbound message (`raw_messages`)

- **Status:** Accepted
- **Date:** 2026-11-03

## Context

The original brief asked for an `unparsed_messages` table for failures. But messages also need to be linked to the transactions they produced, and a message that fails today should become parseable after a parser fix.

## Decision

A single `raw_messages` table holds **every** inbound message with a `parse_status` (`PARSED`, `UNPARSED`, `IGNORED`, `DUPLICATE`) and an optional link to its transaction. `unparsed_messages` is a **view** over it.

## Alternatives

- **Separate `unparsed_messages` table.** Re-parsing would mean *moving* rows between tables, and parsed messages would keep no evidence.
- **Store only parsed transactions.** Debugging a wrong amount would be impossible, and reconciliation would have no audit trail.

## Consequences

- ➕ A full audit trail: every transaction links to all the messages (SMS, email, statement line) that reported it.
- ➕ Re-parsing is an `UPDATE` on status, not a move between tables.
- ➖ More rows stored, which is trivial at this scale. Sensitive text is kept, so OTPs are redacted before storage.
