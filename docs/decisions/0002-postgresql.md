# ADR-0002: PostgreSQL as the database

- **Status:** Accepted
- **Date:** 2026-11-03

## Context

The data is **financial and relational**: accounts have transactions, transactions have categories and evidence messages, and rules point at categories. The most important correctness guarantees are deduplication and integrity. They must hold even under bugs and concurrent requests. Queries are aggregations ("spend by category per month") and filters.

## Options considered

| Option | Verdict |
|---|---|
| **PostgreSQL** ✅ | Fits every requirement below |
| MySQL | Good and common, but **no partial indexes** (our core dedup constraint) and no Row-Level Security; a weaker fit |
| MongoDB | Flexible documents, but our data is relational; integrity constraints and joins are weaker; aggregations over money would push correctness into application code |
| DynamoDB | Scales massively, but needs access patterns designed upfront; ad-hoc analytics ("show me X by Y") is painful; overkill here |
| SQLite | Great for embedded, single-process use; not for a networked multi-user server |

## Decision

**PostgreSQL 16.**

## The specific Postgres features this project relies on

| Feature | Where we use it |
|---|---|
| **ACID transactions** | One ingest = one atomic transaction (account + transaction + raw message) |
| **Partial unique index** | `UNIQUE (account_id, direction, reference_no) WHERE reference_no IS NOT NULL`: dedup layer 2 |
| **CHECK constraints with regex** | `last4 ~ '^[0-9]{4}$'`: makes storing full card numbers impossible |
| **`TIMESTAMPTZ`** | Correct instants; IST month boundaries via `AT TIME ZONE` |
| **Views** | `unparsed_messages` |
| **SQL aggregates and window functions** | Monthly summaries, trends (Phase 3) |
| **Row-Level Security** | Database-enforced isolation between users (planned, before friends join) |
| **`pgvector` extension** | Embeddings for AI features: similar merchants, semantic search (Phase 9). Avoids a separate vector database at this scale |
| **JSONB** | Available if a parser ever needs to keep semi-structured extras |
| **Managed options** | AWS RDS / Aurora when we want backups and patching handled for us |

## Consequences

- ➕ Integrity is enforced by the database, so application bugs cannot create a duplicate referenced payment or store a full card number.
- ➕ One database covers relational data, analytics, multi-tenancy (RLS) and vector search (pgvector), with fewer moving parts to operate.
- ➕ Postgres skills (indexing, query plans, locking, migrations) are among the most transferable in backend and platform work.
- ➖ Vertical scaling limits exist, but they are many orders of magnitude beyond this project's needs.
- ➖ Tests need Docker (Testcontainers), which costs a few seconds per test run. Worth it: tests on H2 would not exercise partial indexes or Postgres time semantics.
