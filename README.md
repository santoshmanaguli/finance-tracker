# finance-tracker

A personal finance tracker for a single user in India. Bank SMS alerts are forwarded from an iPhone by an iOS Shortcut. The backend parses each SMS, removes duplicates and assigns a category, and a PWA dashboard (later phase) displays the results.

**Stack:** Java 21 · Spring Boot 4.1 · PostgreSQL 16 · Flyway · Spring Data JPA · Docker Compose · JUnit 5 + Testcontainers

**Banks:** HDFC, SBI, OneCard (credit card)

| Phase | Scope | Status |
|---|---|---|
| 1 | Foundation: skeleton, compose, schema, health | ✅ |
| 2 | SMS ingest, parsers, dedup, categorization, iOS Shortcut | 🟡 framework done; bank parsers need samples |
| 3 | Read APIs: list/filter, monthly summary, recategorize | |
| 4 | PWA dashboard | |
| 5 | Statement import + reconciliation | |
| 6 | Gmail alert ingestion | |
| 7 | AWS deploy, HTTPS, CI/CD, Terraform | |
| 8 | Observability + "no transactions in 3 days" alert | |

---

## Running it

### Prerequisites
- Docker Desktop (or Docker Engine + Compose v2)
- JDK 21, only needed to run outside Docker or run tests. Maven itself is **not** needed, because `./mvnw` downloads the pinned version.

### Option A: everything in Docker
```bash
cp .env.example .env        # then edit DB_PASSWORD
docker compose up --build
curl localhost:8080/actuator/health
# {"groups":["liveness","readiness"],"status":"UP"}
```

### Option B: Postgres in Docker, app from your IDE or terminal (fast inner loop)
```bash
docker compose up -d postgres
DB_PASSWORD=<same as .env> ./mvnw spring-boot:run
```

### Tests
```bash
./mvnw verify
```
Docker must be running. Testcontainers starts a throwaway `postgres:16-alpine`, runs every Flyway migration against it, and the tests run against that real database.

### Useful commands
```bash
docker compose exec postgres psql -U finance -d finance   # SQL shell
docker compose down          # stop, keep data
docker compose down -v       # stop AND delete the database volume
```

---

## Folder structure

```
finance-tracker/
├── pom.xml                      # dependencies (versions managed by the Spring Boot parent BOM)
├── mvnw, .mvn/                  # Maven Wrapper: pins Maven 3.9.x for everyone, including CI
├── Dockerfile                   # multi-stage: JDK build stage -> JRE runtime, non-root
├── docker-compose.yml           # local stack: postgres + app
├── .env.example                 # template; real .env is git-ignored
└── src/
    ├── main/java/in/santosh/finance/
    │   ├── FinanceApplication.java
    │   ├── common/              # cross-cutting: Clock/time zone, error handling, config
    │   ├── account/             # Phase 2: Account entity + repository
    │   ├── transaction/         # Phase 2-3: Transaction entity, dedup, read APIs
    │   ├── categorization/      # Phase 2: Category, MerchantRule, CategorizationService
    │   └── ingest/              # Phase 2: POST /api/ingest/sms, parser/ (one class per bank)
    ├── main/resources/
    │   ├── application.yml      # only ${ENV_VAR:default} placeholders, no secrets
    │   └── db/migration/        # Flyway: V1__core_schema.sql, V2__seed_categories.sql, ...
    └── test/java/...            # integration tests on real Postgres via Testcontainers
```

### Package-by-feature, layered inside each feature

Each feature package is organised as:

```
ingest/
├── SmsIngestController   ← HTTP only: parse JSON, validate, map to/from DTOs, status codes
├── SmsIngestService      ← business rules + @Transactional boundary
├── RawMessageRepository  ← persistence only (Spring Data interface)
└── RawMessage            ← domain: JPA entity / value objects
```

**Dependency rule:** calls go Controller → Service → Repository, never in the other direction. A repository never calls a service, and a controller never calls a repository directly.

**Why the layers?** Each one has one reason to change. If the iOS Shortcut's JSON format changes, only the controller changes. A new dedup rule changes only the service. A new query changes only the repository. You can also test each layer at the right level: parsers are pure unit tests, and services and repositories are integration tests against real Postgres.

**Why group by feature instead of by layer?** With top-level `controller/`, `service/` and `repository/` folders, one feature is spread across the whole tree, and every class must be `public` so the folders can see each other. Grouping by feature keeps related code together. Classes can then be package-private, so the compiler enforces boundaries (for example, nothing outside `ingest` can call the parsers directly). If a feature ever needs to become its own service, the seams already exist. This is the "modular monolith" approach, and Spring Modulith formalises it.

---

## Database design (V1)

| Table | Purpose |
|---|---|
| `accounts` | Institution + type + **last 4 digits only** (a CHECK constraint enforces `^[0-9]{4}$`) |
| `categories` | Flat list. `Uncategorized` is the fallback |
| `merchant_rules` | Normalized merchant pattern → category; `USER` rules beat `SYSTEM` rules |
| `raw_messages` | **Every** inbound message, parsed or not. The evidence log. Never deleted |
| `transactions` | The "golden record": one row per real-world transaction, no matter how many sources reported it |
| `unparsed_messages` (view) | `raw_messages WHERE parse_status = 'UNPARSED'` |

### Deduplication, in three layers

| Layer | Problem | Mechanism |
|---|---|---|
| 1. Retry idempotency | The Shortcut sends the same SMS twice | `UNIQUE(content_hash)` on `sha256(source\|sender\|body)`. The phone timestamp is left out of the hash |
| 2. Same transaction, reference number present | UPI SMS, email and statement all carry the same UPI ref | Partial unique index `(account_id, direction, reference_no) WHERE reference_no IS NOT NULL`. A second source links its raw message to the existing transaction |
| 3. Same transaction, no reference number | Card swipe alerts often carry no reference | Application-level match on account + amount + direction, with `txn_time` within ±15 min (or the same IST day for statements). **Deliberately not a constraint**, because two identical ₹20 chai payments 10 minutes apart are both real. Statement matches are flagged for review, not auto-merged |

The principle: put a rule in the database when it is absolute, and in code plus human review when it is a heuristic. Silently merging two real transactions is worse than showing a duplicate.

### Other decisions
- **Money is `BIGINT` paise.** Binary floats cannot represent 0.10 exactly, and `0.1 + 0.2 != 0.3`.
- **`TIMESTAMPTZ` everywhere, stored as UTC.** "This month" is computed in `Asia/Kolkata`.
- **`VARCHAR` + `CHECK` instead of native `ENUM`.** Integrity is the same, and adding a value is a one-line migration.
- **`BIGINT` identity keys instead of UUIDs.** The server generates every ID, so this gives smaller indexes and readable IDs.
- **`category_source` (`DEFAULT/RULE/USER/LLM`).** A manual recategorization is never overwritten by rules, and there is room for an LLM fallback later.
- **Hibernate `ddl-auto: validate`.** Flyway owns the schema, and Hibernate only checks that entities match it.
- **`spring.jpa.open-in-view: false`.** No lazy-loading SQL is fired from the web layer while a response is being rendered.
- Postgres does **not** index foreign key columns automatically, so V1 creates those indexes explicitly.

---

## API

### `POST /api/ingest/sms`
Called by the iOS Shortcut ([setup guide](docs/ios-shortcut.md)). Requires the `X-Api-Token` header, whose value must equal the `INGEST_API_TOKEN` env var. If that env var is unset, **every** request is rejected (fail closed).

```bash
curl -i localhost:8080/api/ingest/sms \
  -H "X-Api-Token: $INGEST_API_TOKEN" -H "Content-Type: application/json" \
  -d '{"sender":"AX-HDFCBK","body":"...masked SMS...","receivedAt":"2026-11-04T10:16:00+05:30"}'
```

| Response | `status` | Meaning |
|---|---|---|
| 201 | `CREATED` | New transaction recorded |
| 200 | `MATCHED_EXISTING` | Parsed; same transaction already known from another source |
| 200 | `ALREADY_RECEIVED` | Exact same message seen before (retry); no-op |
| 200 | `IGNORED` | OTP / promo; OTP text is redacted before storage |
| 200 | `UNPARSED` | Stored in `unparsed_messages` for review; never dropped |
| 400 | — | Invalid payload (`application/problem+json`) |
| 401 | — | Missing or wrong token |
| 409 | — | Two identical requests raced; the first one won, nothing lost |

### Ingest pipeline

```
Shortcut ──HTTPS──▶ ApiTokenFilter ──▶ SmsIngestController ──▶ SmsIngestService (one DB transaction)
                                                                   │
                       1. content hash seen? ──yes──▶ ALREADY_RECEIVED
                       2. SmsParserRegistry: OTP? ▶ IGNORED · route by sender header ▶ bank parser
                       3. AccountService.findOrCreate(institution, type, last4)
                       4. TransactionRecorder: ref match? fuzzy cross-source match? else create + categorize
                       5. save RawMessage linked to the transaction
```

### Adding a bank parser
1. Put masked sample SMS in a test: `src/test/java/.../ingest/parser/<Bank>SmsParserTest.java`.
2. Implement `SmsParser` as a `@Component` in `ingest/parser/`, returning `Parsed` / `Ignored` / `Failed`.
3. Return its sender header(s) from `senderCodes()`, for example `HDFCBK`. Startup fails if two parsers claim the same header.

---

## Security baseline
- Secrets come only from environment variables (`.env` locally, a secrets manager in production) and never from code or `application.yml`.
- Full card and account numbers are never stored, only the last 4 digits, and the schema enforces this.
- `.gitignore` excludes `.env`, `statements/`, `samples/`, `*.pdf`, `*.csv` and `*.xlsx`. Parser test fixtures must be **masked** samples.
- The ingest token is compared in constant time, and SMS bodies are never written to logs.
- Compose binds Postgres and the app to `127.0.0.1`, not to every network interface.
- Actuator exposes only `health` and `info`, with details hidden.
- The container runs as a non-root user.
