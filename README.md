# finance-tracker

A personal finance tracker (India). Bank SMS alerts are forwarded from the phone. The backend parses them, removes duplicates, categorizes them, and keeps every message as evidence. A React Native (Expo) app is planned.

**Stack:** Node 24 · TypeScript · Fastify 5 · Zod · Kysely · PostgreSQL 16 · dbmate · Vitest + Testcontainers · Docker Compose · pnpm monorepo
**Banks:** HDFC, SBI, OneCard (credit card)

📚 **Documentation: [`docs/`](docs/README.md)**: [architecture](docs/architecture.md), [roadmap](docs/roadmap.md), [decision records](docs/README.md#architecture-decision-records). The original Java/Spring Boot version is preserved on the `archive/java-v0` branch.

---

## Run it

### Prerequisites
- Docker Desktop (or Docker Engine + Compose v2)
- For running outside Docker or running tests: Node 22.18+ (24 LTS recommended) and pnpm (`corepack enable`)

### Option A: everything in Docker
```bash
cp .env.example .env          # set DB_PASSWORD and INGEST_API_TOKEN (openssl rand -base64 48)
docker compose up --build
curl localhost:8080/health/ready        # {"status":"UP"}
```
Compose starts `postgres`, then the one-shot `migrate` container (applies `db/migrations`, then exits), then `api`.

### Option B: Postgres in Docker, API on your machine (fast inner loop)
```bash
pnpm install
docker compose up -d postgres
docker compose run --rm migrate
pnpm --filter @finance/api dev          # Node runs the TypeScript directly, restarts on change
```

### Tests and checks
```bash
pnpm --filter @finance/api test                         # unit + integration (Docker must be running)
pnpm --filter @finance/api exec vitest run --project unit   # unit only, ~0.5 s
pnpm --filter @finance/api typecheck
pnpm lint                                               # Biome: lint + format check
```
Integration tests start a throwaway `postgres:16-alpine`, apply the **same dbmate migrations** as production, and run against it.

### Useful commands
```bash
docker compose exec postgres psql -U finance -d finance     # SQL shell
docker compose logs -f api                                   # structured JSON logs
docker compose down          # stop, keep data
docker compose down -v       # stop AND delete the database volume
```

---

## Repository layout

```
finance-tracker/
├── apps/
│   └── api/                      # Fastify backend
│       ├── src/
│       │   ├── server.ts         # entry: listen + graceful shutdown (SIGTERM)
│       │   ├── app.ts            # buildApp(): wiring; used by server.ts and tests
│       │   ├── config.ts         # env vars validated with Zod (fail fast)
│       │   ├── db.ts             # Kysely + pg pool, table types, safe BIGINT parsing
│       │   ├── common/           # auth hook, RFC 9457 errors, health probes, clock
│       │   ├── ingest/           # route, ingestSms(), contentHash, parsers/
│       │   ├── transaction/      # recordTransaction(): dedup layers 2 + 3
│       │   ├── account/          # race-safe findOrCreateAccount()
│       │   └── categorization/   # normalizeMerchant(), categorize()
│       ├── test/                 # unit/ (pure) and integration/ (Testcontainers)
│       └── Dockerfile            # multi-stage, non-root, prod deps only
├── db/migrations/                # dbmate plain-SQL migrations (source of truth for the schema)
├── docs/                         # architecture, ADRs, roadmap, plans, guides
├── docker-compose.yml            # postgres -> migrate -> api
└── pnpm-workspace.yaml           # apps/mobile + packages/shared arrive in Step 7
```

**Layering inside each module:** routes (HTTP only) → service functions (business rules, one DB transaction) → repository functions (SQL) → plain types. Dependencies are passed as function arguments, which is what makes everything testable. Rationale: [ADR-0006](docs/decisions/0006-package-by-feature.md).

---

## API

### `POST /api/ingest/sms`
Called by the iOS Shortcut ([setup guide](docs/ios-shortcut.md)). Requires the header `X-Api-Token: <INGEST_API_TOKEN>`. If the env var is unset, **every** request is rejected (fail closed).

```bash
curl -i localhost:8080/api/ingest/sms \
  -H "X-Api-Token: $INGEST_API_TOKEN" -H "Content-Type: application/json" \
  -d '{"sender":"AX-HDFCBK","body":"...masked SMS...","receivedAt":"2026-11-04T10:16:00+05:30"}'
```

| Response | `status` | Meaning |
|---|---|---|
| 201 | `CREATED` | New transaction recorded |
| 200 | `MATCHED_EXISTING` | Parsed; same transaction already known from another source |
| 200 | `ALREADY_RECEIVED` | Exact same message seen before (retry or parallel automation); no-op |
| 200 | `IGNORED` | OTP / promo; OTP text is redacted before storage |
| 200 | `UNPARSED` | Stored in `unparsed_messages` for review; never dropped |
| 400 | — | Invalid payload (`application/problem+json`) |
| 401 | — | Missing or wrong token |

Until the bank parsers exist (roadmap Step 5), every real SMS returns `UNPARSED`. That is by design: nothing is lost, and it can be re-parsed later.

### Health
- `GET /health/live`: the process is up (never checks dependencies)
- `GET /health/ready`: the database is reachable (503 otherwise)

### Ingest pipeline
```
Shortcut ──HTTPS──▶ ingestAuth hook ──▶ ingestRoutes (Zod) ──▶ ingestSms() (one DB transaction)
     1. content hash seen?                    ──yes──▶ ALREADY_RECEIVED
     2. parser registry: OTP? ▶ IGNORED · route by sender header ▶ bank parser
     3. findOrCreateAccount(institution, type, last4)
     4. recordTransaction(): ref match? fuzzy cross-source match? else create + categorize
     5. insert raw_message linked to the transaction
     (a concurrent duplicate hits a unique index → one automatic retry → ALREADY_RECEIVED)
```

### Adding a bank parser
1. Put masked sample SMS in a test: `apps/api/test/unit/<bank>Parser.test.ts`.
2. Implement `SmsParser` in `apps/api/src/ingest/parsers/<bank>.ts`, returning `parsed` / `ignored` / `failed` (use `parsedTransaction()` and `toPaise()`).
3. Add it to `bankParsers` in `parsers/index.ts`. Startup fails if two parsers claim the same sender header.

---

## Security baseline
- Secrets only from environment variables (`.env` locally, git-ignored).
- Full card and account numbers are never stored: last 4 digits only, enforced by a database CHECK and by the code.
- Constant-time token comparison; tokens, cookies and authorization headers are redacted from logs; SMS bodies are never logged.
- `.gitignore` excludes `.env`, `statements/`, `samples/`, `*.pdf`, `*.csv`, `*.xlsx`.
- Compose binds Postgres and the API to `127.0.0.1` only. The container runs as the non-root `node` user with production dependencies only.
