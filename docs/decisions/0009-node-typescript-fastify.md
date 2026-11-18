# ADR-0009: Node.js + TypeScript + Fastify replaces Java + Spring Boot

- **Status:** Accepted
- **Date:** 2026-11-18
- **Supersedes:** [ADR-0001](0001-java-spring-boot.md)

## Context

The owner's career direction changed: from backend/platform with Java toward **DevOps + AI/LLM infrastructure**. Application code will be written mostly by AI, and the owner's hands-on time goes into infrastructure (CI/CD, Terraform, Kubernetes, observability, LLM serving). The owner works full stack daily (including .NET at work) and already knows Node and TypeScript.

What we need from the backend language now:
- Least friction for the owner to **read, review and debug** AI-written code.
- One language across API, mobile app and shared types.
- A good fit for an I/O-bound API (receive a message, parse, a few SQL queries).

## Options

| Option | Verdict |
|---|---|
| **Node + TypeScript + Fastify** ✅ | Known to the owner; same language as the Expo app, so **validation schemas and types are shared** via `packages/shared`; very fast for I/O-bound APIs; small images |
| .NET (ASP.NET Core) | Excellent and used at the owner's job, but it would be a second language next to the TypeScript app, and it adds no new learning |
| Java + Spring Boot (previous) | Solid, but no longer aligned with the career direction |
| NestJS (on Node) | More structure (Spring-like), but heavier and more magic; Fastify keeps HTTP visible |

## Decision

| Concern | Choice | Why |
|---|---|---|
| Runtime | **Node.js 24 LTS** | A mature LTS supported to April 2028; Node 26 became LTS only in Oct 2026, so we let the ecosystem settle first |
| HTTP framework | **Fastify 5** | Fast, schema-first, built-in structured logging (pino) |
| Validation + types | **Zod**, shared with the app | One schema gives runtime validation *and* TypeScript types |
| Database access | **Kysely** (type-safe SQL query builder) + `pg` | Our design relies on Postgres features (partial indexes, CHECKs, native queries); an ORM like Prisma hides SQL; Kysely keeps SQL visible *and* typed |
| Migrations | **dbmate** with plain `.sql` files | Language-agnostic (a single binary or Docker image), so migrations run as a CI step or Kubernetes Job; the existing V1 SQL carries over |
| Tests | **Vitest** + **Testcontainers** (`@testcontainers/postgresql`) | The same real-Postgres testing approach as before |
| Lint + format | **Biome** | One fast tool instead of ESLint + Prettier |
| Package manager | **pnpm** workspaces | Monorepo: `apps/api`, `apps/mobile`, `packages/shared` |

## Consequences

- ➕ One language end to end; the owner can review every line comfortably.
- ➕ Smaller container images and faster startup than the JVM, which suits small free-tier VMs.
- ➕ All design work carries over unchanged: schema, 3-layer dedup, evidence log, thin clients, test cases and ADRs.
- ➖ The existing Java code must be ported (AI-heavy work); `main` keeps the Java version until the Node port reaches parity on the `node-port` branch.
- ➖ TypeScript types are erased at runtime, so every external input **must** be validated (Zod) at the edge. With Java, types partly did that job.
- ➖ Money stays as integer paise. In JS, `number` is safe up to 2^53 paise (about ₹90 trillion), so that's fine. But Postgres `BIGINT` must be parsed into `number` explicitly (the `pg` driver returns strings by default), and parsing must use string arithmetic, never floats.
- 🔭 The AI phase will add a small **Python** service, because that's where the LLM ecosystem is.
