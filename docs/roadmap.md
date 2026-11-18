# Roadmap

> **Direction since 2026-11-18:** Node + TypeScript backend ([ADR-0009](decisions/0009-node-typescript-fastify.md)), free hosting ([ADR-0010](decisions/0010-free-hosting.md)), and a DevOps + AI/LLM learning focus. **The AI writes most application code; the owner builds the infrastructure.**

## How every step runs

```
plan in docs/plans/ → owner approves → feature branch → code + tests → pull request
→ CI green → owner reviews the diff → merge to main → (from Step 4) auto-deploy
```

## Steps

| # | Step | Mainly | Owner learns | Status |
|---|---|---|---|---|
| 0 | **Reset:** archive Java (`archive/java-v0`), ADRs 0009/0010, new roadmap | AI | Decision records | ✅ Done |
| 1 | **Port the backend to Node**: pnpm monorepo, Fastify API, same schema and test cases ([plan](plans/step-1-node-port.md)) | AI | Reviewing a port; monorepos | ✅ Built on `node-port` (72 tests); awaiting owner review + merge |
| 2 | **CI from day one**: GitHub Actions lint → test (Postgres) → build image → Trivy scan → push to GHCR | **Owner** | CI pipelines, registries, supply-chain security | |
| 3 | **Multi-user + Google sign-in** ([plan, to be rewritten for Node](plans/phase-2.5-multi-user.md)) | AI | OIDC, JWT, token rotation | |
| 4 | **First real deploy**: Oracle VM + Docker Compose + Caddy + DuckDNS. The iPhone Shortcut goes live | **Owner** | Linux, firewalls, TLS, DNS | |
| 5 | **Bank parsers**: HDFC, SBI, OneCard | AI | | ⏳ Needs masked SMS samples |
| 6 | **Read APIs + "what was it for"**: notes, tags, payee rules, splits | AI | | |
| 7 | **App**: Expo web build → Cloudflare Pages; APK via GitHub Actions | AI (app), **owner** (pipelines) | Frontend and mobile CI/CD | |
| 8 | **Terraform**: VM, network, firewall, DNS as code | **Owner** | Infrastructure as code, state | |
| 9 | **Kubernetes + GitOps**: k3s, Helm chart, Argo CD | **Owner** | Kubernetes, Helm, GitOps | |
| 10 | **Observability**: metrics, logs, dashboards, Healthchecks.io dead-man's switch | **Owner** | Monitoring, SLOs, alerting | |
| 11 | **Statements + Gmail** capture routes | AI | Scheduled jobs | |
| 12 | **AI service (Python)**: LLM categorization, fallback parser, plain-English questions with pgvector, self-hosted Ollama on k3s | Both | LLM serving, evaluation, AI infra | |

## Pending from the owner

- [ ] **Review the `node-port` branch, open the pull request, and merge it.**
- [ ] **Hands-on tasks #1 and #2** in `docker-compose.yml` (startup ordering; see the TODOs).
- [ ] **Masked sample SMS:** 3–5 each from HDFC, SBI and OneCard, with sender IDs (exact spacing and punctuation; digits masked as `XX1234`).
- [ ] **Account details:** which HDFC and SBI products; OneCard's issuing bank.
- [ ] Before Step 4: create an **Oracle Cloud** account (Always Free) and a **DuckDNS** subdomain.
- [ ] Before Step 7: a free Google Cloud project + OAuth client IDs (steps will be provided).

## Decisions log

| Date | Decision |
|---|---|
| 2026-11-03 | Stack: Java 21, Spring Boot, Postgres 16 ([0001](decisions/0001-java-spring-boot.md), [0002](decisions/0002-postgresql.md)) |
| 2026-11-03 | Banks: HDFC, SBI, OneCard |
| 2026-11-16 | Go multi-user; Google sign-in ([0007](decisions/0007-multi-user-google-login.md)) |
| 2026-11-16 | App: React Native + Expo; iPhone uses the web build ([0008](decisions/0008-react-native-expo-client.md)) |
| 2026-11-16 | AI after the app exists |
| 2026-11-18 | **Java dropped → Node 24 + TypeScript + Fastify** ([0009](decisions/0009-node-typescript-fastify.md)); Java preserved on `archive/java-v0` |
| 2026-11-18 | **Free tools only:** Oracle Always Free, DuckDNS, Cloudflare Pages, GitHub Actions ([0010](decisions/0010-free-hosting.md)) |
| 2026-11-18 | Owner focus: DevOps + AI/LLM; owner writes the infrastructure, AI writes the application code |
