# ADR-0006: Package-by-feature modular monolith

- **Status:** Accepted
- **Date:** 2026-11-03

## Decision

One deployable service. Code is grouped by **feature** (`ingest`, `transaction`, `account`, `categorization`, later `auth`, `statement`, `ai`), with controller → service → repository → domain layering *inside* each feature.

## Alternatives

- **Package-by-layer** (top-level `controller/`, `service/`, `repository/`). One feature is scattered across the tree, everything must be `public`, and nothing enforces boundaries.
- **Microservices.** Network calls, distributed transactions and many deploy pipelines; none of it pays off for a single developer at this scale.

## Consequences

- ➕ Related code lives together; package-private visibility lets the compiler enforce boundaries.
- ➕ Clear seams, so a module could be extracted into a service later if a real need appears.
- ➖ Boundary discipline is by convention. A tool such as Spring Modulith or ArchUnit could enforce it later.
