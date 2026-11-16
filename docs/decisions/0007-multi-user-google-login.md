# ADR-0007: Multi-user with Google sign-in and our own tokens

- **Status:** Accepted (implementation: Phase 2.5)
- **Date:** 2026-11-16

## Context

Friends want to use the app too. Phases 1–2 assumed a single user (one static token, no `user_id`). Adding users after real data exists would mean a risky data migration, so it is done now while the tables are empty.

## Decision

1. **Identity:** Sign in with Google (OpenID Connect). It is free and we never handle passwords. Sign-up is **invite-only** (an email allowlist).
2. **Sessions:** the backend verifies Google's ID token once, then issues **our own** short-lived access token (JWT, 15 min) and a rotating refresh token (30 days, stored hashed; reuse of an old one revokes the whole family).
3. **Machine clients:** iOS Shortcuts use **per-device tokens**, generated in the app, stored as SHA-256 hashes, individually revocable, and limited to `/api/ingest/**`.
4. **Isolation:** `user_id` on every owned table, every query scoped by user, then **Postgres Row-Level Security** as defence in depth before any friend's real data arrives.
5. **Framework:** Spring Security + OAuth2 Resource Server (approved 2026-11-16).

## Why our own tokens instead of passing Google's token everywhere

- We control session lifetime and revocation (a lost phone means revoking its tokens).
- We are not coupled to one identity provider (Apple or passkeys can be added later).
- Every request verifies locally against our own key, with no round trip to Google.

## Consequences

- ➕ No passwords to store or leak; free; a familiar login for friends.
- ➖ Holding friends' financial data is a real responsibility: encryption at rest, backups, a "delete my data" feature, and awareness of India's DPDP Act 2023.
- ➖ **Gmail ingestion (Phase 6) is harder for friends than for the owner.** Gmail read access is a Google "restricted" scope. In testing mode, consent expires every 7 days (max 100 test users); full approval needs a paid security assessment. Sign-in itself is unaffected.
