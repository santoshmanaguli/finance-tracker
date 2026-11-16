# Phase 2.5 plan: multi-user + Google sign-in

- **Status:** Proposed, awaiting owner approval
- **Decision record:** [ADR-0007](../decisions/0007-multi-user-google-login.md)
- **New dependencies (approved 2026-11-16):** `spring-boot-starter-security`, `spring-boot-starter-oauth2-resource-server`

## Sign-in flow

```mermaid
sequenceDiagram
    participant App
    participant Google
    participant API as Backend
    participant DB

    App->>Google: Sign in with Google
    Google-->>App: ID token (JWT signed by Google)
    App->>API: POST /api/auth/google {idToken}
    API->>API: verify signature (Google public keys), issuer, audience = our client IDs, expiry, email_verified
    API->>DB: email invited? find or create user
    API->>DB: store hash of new refresh token
    API-->>App: {accessToken (15 min JWT), refreshToken (30 days)}
    App->>API: GET /api/... Authorization: Bearer accessToken
    Note over App,API: When it expires: POST /api/auth/refresh → new pair. The old refresh token is revoked;<br/>presenting a revoked one revokes the whole family (theft detection).
```

## Who can call what

| Caller | Credential | Allowed |
|---|---|---|
| App (iPhone web, Android) | Our access token | All `/api/**` for **their own** data |
| Android capture module | Our access token | `/api/ingest/**` |
| iPhone Shortcut | Device token (`Authorization: Bearer ft_dev_...`) | `/api/ingest/**` only |
| Anyone | none | `/actuator/health`, `/api/auth/**` |

## Endpoints

| Method | Path | Purpose |
|---|---|---|
| POST | `/api/auth/google` | Exchange a Google ID token for our tokens |
| POST | `/api/auth/refresh` | Rotate the refresh token |
| POST | `/api/auth/logout` | Revoke the refresh token family |
| GET | `/api/me` | Current user |
| POST | `/api/devices` | Create a device token. **The plaintext is returned once**, only the hash is stored |
| GET | `/api/devices` | List my device tokens (name, last used) |
| DELETE | `/api/devices/{id}` | Revoke one |
| POST | `/api/ingest/sms` | Unchanged contract; now attributed to the caller's user |

## Schema: migration `V3__multi_user.sql`

The tables are still empty, so `NOT NULL user_id` columns can be added directly. V1 is never edited.

```sql
CREATE TABLE users (
    id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    email         VARCHAR(320) NOT NULL UNIQUE,      -- lowercased
    google_sub    VARCHAR(255) UNIQUE,               -- Google's stable user id; null until first sign-in
    display_name  VARCHAR(128),
    status        VARCHAR(16)  NOT NULL CHECK (status IN ('INVITED','ACTIVE','DISABLED')),
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE device_tokens (
    id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id       BIGINT       NOT NULL REFERENCES users(id),
    name          VARCHAR(64)  NOT NULL,             -- 'Santosh iPhone Shortcut'
    token_hash    CHAR(64)     NOT NULL UNIQUE,      -- sha256(token); plaintext never stored
    last_used_at  TIMESTAMPTZ,
    revoked_at    TIMESTAMPTZ,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE refresh_tokens (
    id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id       BIGINT       NOT NULL REFERENCES users(id),
    family_id     UUID         NOT NULL,             -- all rotations of one login
    token_hash    CHAR(64)     NOT NULL UNIQUE,
    expires_at    TIMESTAMPTZ  NOT NULL,
    revoked_at    TIMESTAMPTZ,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- Ownership on every user-owned table
ALTER TABLE accounts       ADD COLUMN user_id BIGINT NOT NULL REFERENCES users(id);
ALTER TABLE transactions   ADD COLUMN user_id BIGINT NOT NULL REFERENCES users(id);
ALTER TABLE raw_messages   ADD COLUMN user_id BIGINT NOT NULL REFERENCES users(id);
ALTER TABLE merchant_rules ADD COLUMN user_id BIGINT REFERENCES users(id);  -- NULL = system rule

-- Uniqueness becomes per user
-- accounts:       (institution, account_type, last4)  ->  (user_id, institution, account_type, last4)
-- raw_messages:   (content_hash)                      ->  (user_id, content_hash)
-- merchant_rules: (pattern, match_type)                ->  unique index on (COALESCE(user_id, 0), pattern, match_type)
-- transactions:   the ref index stays on account_id (an account belongs to exactly one user)
-- New indexes: transactions (user_id, txn_time) for "my recent transactions"
```

**Why `user_id` on `transactions` when the account already implies the user?** It is deliberate denormalization: every per-user query avoids a join, and Row-Level Security policies stay simple (`user_id = current_user_id`).

**Seeding the first user:** the owner's email is inserted as `INVITED` from an env var (`FINANCE_OWNER_EMAIL`) on startup, so no personal data lives in the migration files.

## Code structure

```
auth/            GoogleIdTokenVerifier, TokenService (issue/verify JWT, rotate refresh), AuthController
user/            User, UserRepository, InviteService
device/          DeviceToken, DeviceTokenRepository, DeviceService, DeviceController
common/security/ SecurityConfig (filter chains), DeviceTokenAuthenticationFilter, CurrentUser
```

The static `ApiTokenFilter` from Phase 2 is **removed** and replaced by device tokens.

## Testing

- Google is never called in tests. A test RSA key pair signs fake Google ID tokens, and the verifier is pointed at the test public key.
- Tests prove that user A **cannot** read, ingest into or revoke anything of user B's. Isolation tests are the most important part of this phase.
- Refresh-token rotation, reuse detection, expiry and revocation each get a test.

## Hands-on piece for the owner

`DeviceTokenRepository`: write the query "active device tokens for a user, newest first" (not revoked), as a derived query method name. A TODO with hints will be in the file, plus a failing test to make pass.

## Out of scope (later phases)

- Row-Level Security policies: before the first friend's real data (Phase 7).
- Google Cloud OAuth client setup: before Phase 4 (step-by-step guide to follow).
- "Delete my data": Phase 4.
