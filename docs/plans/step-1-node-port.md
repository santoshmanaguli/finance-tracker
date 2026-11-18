# Step 1 plan: port the backend to Node + TypeScript

- **Status:** ✅ Implemented on `node-port` (2026-11-18), awaiting owner review
- **Branch:** `node-port` → pull request into `main`
- **Decision:** [ADR-0009](../decisions/0009-node-typescript-fastify.md)

## Goal

**Feature parity with the Java version (Phases 1–2), nothing more.** Same database schema, same API contract, same behaviour, same test cases. New features wait until after the port. Mixing a rewrite with new features makes it impossible to tell whether a bug comes from the port or from the feature.

## Repository layout (pnpm monorepo)

```
finance-tracker/
├── apps/
│   ├── api/                  # Fastify backend (this step)
│   │   ├── src/
│   │   │   ├── server.ts         # entry point: build app, listen, graceful shutdown
│   │   │   ├── app.ts            # buildApp(): plugins + routes (testable without a port)
│   │   │   ├── config.ts         # env vars validated with Zod; fail fast at startup
│   │   │   ├── db.ts             # Kysely + pg pool; BIGINT → number parsing
│   │   │   ├── common/           # auth hook, error handler (RFC 9457), clock
│   │   │   ├── ingest/           # route, service, contentHash, parsers/
│   │   │   ├── transaction/      # recorder (dedup layers 2 and 3), repository
│   │   │   ├── account/
│   │   │   └── categorization/   # normalizer, rules, service
│   │   ├── test/                 # vitest: unit + integration (Testcontainers)
│   │   ├── Dockerfile
│   │   └── package.json
│   └── mobile/               # Expo app (Step 7; empty for now)
├── packages/
│   └── shared/               # Zod schemas + types shared by api and mobile
├── db/
│   └── migrations/           # dbmate .sql files (V1 schema carried over unchanged)
├── docs/
├── docker-compose.yml        # postgres + migrate (dbmate) + api
├── pnpm-workspace.yaml
└── biome.json
```

## Java → Node mapping

| Java (archive/java-v0) | Node | Notes |
|---|---|---|
| Spring Boot app + `application.yml` | `app.ts` + `config.ts` (Zod-validated env) | Same env var names (`DB_URL` style, `INGEST_API_TOKEN`) |
| Flyway `V1__core_schema.sql` | dbmate `db/migrations/20261103000000_core_schema.sql` | Same SQL, dbmate up/down markers added |
| `ApiTokenFilter` | Fastify `onRequest` hook on `/api/ingest/*` | Constant-time compare (`crypto.timingSafeEqual`), fails closed |
| `GlobalExceptionHandler` | `setErrorHandler` → `application/problem+json` | 400 validation, 409 unique violation (Postgres code `23505`) |
| JPA entities + Spring Data repositories | Kysely typed tables + repository functions | Explicit SQL; the cross-source dedup query moves over verbatim |
| `@Transactional` | `db.transaction().execute(async trx => …)` | One DB transaction per ingest, passed explicitly |
| `SmsParser` + sealed `ParseResult` | `SmsParser` interface + discriminated union `{kind: 'parsed' \| 'ignored' \| 'failed'}` | `switch` with a `never` check for exhaustiveness |
| `Amounts` (BigDecimal) | String-based rupees → paise | No floating-point math anywhere |
| `TestParsersConfig` (fake bank) | Test-only parsers injected via `buildApp({ parsers })` | Dependency injection by function arguments, no container |
| JUnit + Testcontainers | Vitest + `@testcontainers/postgresql` | **All 69 existing test cases ported**, 1:1 |
| Actuator `/actuator/health` | `/health/live` and `/health/ready` (DB ping) | Kubernetes-style probes, ready for Step 9 |

## Docker and local development

- `docker compose up` → Postgres → `migrate` (dbmate runs once and exits) → `api`.
  - Migrations as a **separate one-shot container** is the same pattern used later as a Kubernetes Job or a CI step.
- Multi-stage Dockerfile: `node:24-alpine` build stage → slim runtime with production dependencies only, run as a non-root user.
- `pnpm dev` runs the API with hot reload against the compose Postgres.

## Definition of done

- [ ] All 69 ported tests pass (unit + integration against real Postgres)
- [ ] `docker compose up` works from a fresh clone; `/health/ready` returns 200
- [ ] Same `POST /api/ingest/sms` request/response contract as documented in the README
- [ ] Logs are structured JSON (pino); SMS bodies and the token header are **redacted**
- [ ] Java sources, `pom.xml` and the Maven wrapper are **removed in the same pull request** (history stays on `archive/java-v0`)
- [ ] README, `architecture.md` and `overview.md` updated for Node

## Owner's hands-on piece (DevOps-flavoured)

Write the **`docker-compose.yml` healthcheck and dependency ordering** for the new `migrate` service: `api` must start only after `migrate` *completed successfully*, and `migrate` only after Postgres is healthy. The hint is `depends_on.condition: service_completed_successfully`. A TODO with guidance will be in the file. This is a small rehearsal for Kubernetes init containers and Jobs.

## Not in this step

CI (Step 2, the owner writes it), multi-user (Step 3), deployment (Step 4).

## Outcome (2026-11-18)

- **72 tests pass** (41 unit + 31 integration), versus 69 in Java. New: concurrent duplicate requests, readiness reporting DOWN when the DB is unreachable, and the seed data.
- `docker compose` stack verified: Postgres → `migrate` (dbmate) → API healthy; auth, idempotency, problem details, log redaction and SIGTERM graceful shutdown were checked against the running container.
- Image: production dependencies + `dist/` only, non-root `node` user.

### Deviations from the plan (and why)

| Plan | What happened | Why |
|---|---|---|
| `packages/shared` now | Deferred to Step 7 | Nothing shares it until the app exists; YAGNI |
| Seed migration was the owner's TODO | Written by AI (categories + 21 merchant rules) | The owner now focuses on infrastructure, and real ingest needs `Uncategorized` |
| Concurrent duplicates return 409 | **One automatic retry** → `ALREADY_RECEIVED` (200) | Two iOS automations ("UPI" + "debited") fire for the same SMS at the same instant; both should succeed |
| `findOrCreate` = SELECT then INSERT | `INSERT ... ON CONFLICT DO NOTHING`, then SELECT | Race-safe; the database arbitrates |
| Owner task: write the whole `migrate` ordering | Two TODOs in `docker-compose.yml` | Same intent; smaller and verifiable |
| TypeScript 5.x assumed | **TypeScript 7** (native compiler) | Current stable; type-checks the whole project in about a second |

### Sandbox notes (not project issues)
The Claude Code cloud sandbox can't download packages *inside* `docker build` (its HTTPS proxy certificate isn't trusted in containers), so the image was verified with a scratch copy of the Dockerfile that injects that certificate. Docker Hub also rate-limits the sandbox, so base images were pulled from AWS's public mirror. Neither affects your laptop or GitHub Actions.
