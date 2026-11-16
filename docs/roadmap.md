# Roadmap

Each phase ends with a review. Nothing in the next phase starts until the owner approves.

| # | Phase | Delivers | Status |
|---|---|---|---|
| 1 | Foundation | Spring Boot skeleton, Docker Compose, Flyway schema, health endpoint | ✅ Done |
| 2 | SMS ingest | `POST /api/ingest/sms`, parser framework, 3-layer dedup, categorization, iOS Shortcut guide | ✅ Framework done |
| 2.5 | **Multi-user + Google sign-in** | Users, our own JWT + refresh tokens, device tokens, per-user data | 📝 [Plan](plans/phase-2.5-multi-user.md): awaiting approval |
| 2b | Bank parsers | HDFC, SBI, OneCard (then friends' banks) | ⏳ Blocked on masked sample SMS |
| 3 | Read APIs + "what was it for" | List/filter, monthly summary, recategorize (creates a rule), notes, tags, UPI payee rules, split transactions | Planned |
| 4 | App (Expo React Native) | iPhone web build first: login, dashboard, inbox, annotations. Then the Android APK with SMS + notification capture | Planned |
| 5 | Statements | PDF/CSV upload, reconciliation, mismatch flags | Planned |
| 6 | Gmail alerts | Second automatic capture route ([caveat for friends](decisions/0007-multi-user-google-login.md)) | Planned |
| 7 | Deploy | AWS + HTTPS (Caddy), GitHub Actions CI/CD (also builds and signs the APK), Terraform, secrets in SSM, encrypted backups, Row-Level Security | Planned |
| 8 | Observability | Structured logs, Micrometer metrics, "no transaction in 3 days" alert | Planned |
| 9 | AI | LLM category suggestions, LLM fallback parser, plain-English questions, later self-hosted models + pgvector | Planned |

## Pending from the owner

- [ ] **Masked sample SMS:** 3–5 each from HDFC, SBI and OneCard, with the sender ID as shown on the phone. Keep exact spacing and punctuation; mask digits (`XX1234`).
- [ ] **Account details:** which HDFC and SBI products (savings, debit card, credit card), and OneCard's issuing bank.
- [ ] **`V2__seed_categories.sql`**: hands-on task. Required before real ingest works (the `Uncategorized` category).
- [ ] **Approve the Phase 2.5 plan.**
- [ ] Before Phase 4: create a free Google Cloud project + OAuth client IDs (steps will be provided).

## Decisions log

| Date | Decision |
|---|---|
| 2026-11-03 | Stack: Java 21, Spring Boot 4.1, Postgres 16, Flyway, Testcontainers ([0001](decisions/0001-java-spring-boot.md), [0002](decisions/0002-postgresql.md)) |
| 2026-11-03 | Banks: HDFC, SBI, OneCard |
| 2026-11-16 | Go multi-user; Google sign-in; Spring Security approved ([0007](decisions/0007-multi-user-google-login.md)) |
| 2026-11-16 | App: React Native + Expo; iPhone uses the web build; Android via sideloaded APK ([0008](decisions/0008-react-native-expo-client.md)) |
| 2026-11-16 | AI after Phase 4 |
