# ADR-0001: Java 21 + Spring Boot for the backend

- **Status:** Accepted
- **Date:** 2026-11-03

## Context

The backend parses messages, enforces money-related integrity, exposes an HTTP API, and will later run scheduled jobs (Gmail polling) and AI calls. It has two goals at once:
1. Work correctly for real money data.
2. Build skills that matter in the owner's target job market: backend and platform roles at product companies, many of them in fintech.

The owner's stated learning priorities put Java and Spring Boot first, and the stack was fixed in the project brief. This ADR records *why that choice also holds up on technical merit*, and what it costs.

## Options considered

| Option | Strengths | Weaknesses for this project |
|---|---|---|
| **Java 21 + Spring Boot** ✅ | Industry standard for backend and fintech in India and globally; strong static typing; huge, mature ecosystem (Spring Data, Security, Actuator, Flyway, Testcontainers); excellent tooling and profiling; Java 21 brings records, sealed types, pattern matching and virtual threads | More verbose; slower startup and higher memory than Go or Node; steeper framework learning curve |
| Node.js + TypeScript | The owner already knows it; fast to write; same language as the app | Doesn't build *new* backend depth; weaker for CPU-heavy work; the ecosystem churns faster |
| Go | Small binaries, low memory; **the language of platform tooling** (Kubernetes, Terraform, Docker, Prometheus) | Less "batteries included" for CRUD, ORM and validation; better learned for building platform tools than business backends |
| Python (FastAPI) | Best AI/ML ecosystem | Weaker typing and tooling for money-correctness code; AI can be called from Java, or added later as a separate service |

## Decision

**Java 21 (an LTS release) with Spring Boot 4.1.**

## Why it fits this project specifically

- **Correctness with money.** Static types, `BigDecimal`, records and sealed interfaces make illegal states hard to represent. `ParseResult` is a sealed type, so the compiler forces every outcome to be handled.
- **Batteries included.** Validation, transactions (`@Transactional`), security, health checks and metrics (Actuator, Micrometer) and migrations are mature, well-documented building blocks. We write business logic, not plumbing.
- **Testing culture.** JUnit 5 + Testcontainers lets the tests run against a real Postgres.
- **Career fit.** Java/Spring is among the most requested backend stacks in Indian product companies and fintech, and interviews probe exactly the concepts this project uses: transactions, JPA, concurrency, indexing.
- **Long-term support.** Java 21 is LTS and Spring Boot has a predictable release and support cycle. We chose Boot 4.x because 3.x has left free OSS support.

## Consequences

- ➕ Production-grade foundations from day one; skills transfer directly to jobs.
- ➖ More boilerplate than Node. Mitigated with records and *no* Lombok, since explicit code helps learning.
- ➖ Higher memory use (about 200–300 MB). Fine on the smallest cloud VMs; container-aware JVM flags are set in the Dockerfile.
- 🔭 **Go is still worth learning later**, specifically for platform tooling such as Kubernetes operators and CLIs. It is a complement, not a replacement.
