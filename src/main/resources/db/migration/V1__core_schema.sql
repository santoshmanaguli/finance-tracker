-- =====================================================================
-- V1: core schema.
-- Rules for this folder:
--   * Migrations are append-only history. Never edit a file after it has
--     run anywhere; write V3, V4, ... instead (Flyway checksums will fail).
--   * Enumerations are VARCHAR + CHECK rather than native ENUM types:
--     same integrity, but adding a value is a one-line migration.
--   * All instants are TIMESTAMPTZ (stored as UTC).
-- =====================================================================

-- ---------------------------------------------------------------------
-- accounts: we only ever store the last 4 digits (never full numbers).
-- institution disambiguates "1234" at HDFC vs "1234" at SBI.
-- ---------------------------------------------------------------------
CREATE TABLE accounts (
    id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    institution   VARCHAR(32)  NOT NULL,             -- 'HDFC', 'SBI', 'ONECARD'
    account_type  VARCHAR(16)  NOT NULL
                  CHECK (account_type IN ('CREDIT_CARD', 'DEBIT_CARD', 'SAVINGS', 'UPI')),
    last4         CHAR(4)      NOT NULL CHECK (last4 ~ '^[0-9]{4}$'),
    nickname      VARCHAR(64),
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT ux_accounts_identity UNIQUE (institution, account_type, last4)
);

-- ---------------------------------------------------------------------
-- categories: flat list. 'Uncategorized' must exist (seeded in V2).
-- ---------------------------------------------------------------------
CREATE TABLE categories (
    id    BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name  VARCHAR(64) NOT NULL,
    CONSTRAINT ux_categories_name UNIQUE (name)
);

-- ---------------------------------------------------------------------
-- merchant_rules: normalized merchant -> category.
-- Lower priority number wins. USER rules (created when you recategorize)
-- get a lower number than SYSTEM seed rules so they always take precedence.
-- ---------------------------------------------------------------------
CREATE TABLE merchant_rules (
    id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    pattern      VARCHAR(128) NOT NULL,              -- compared to transactions.merchant_norm
    match_type   VARCHAR(16)  NOT NULL CHECK (match_type IN ('EXACT', 'CONTAINS')),
    category_id  BIGINT       NOT NULL REFERENCES categories (id),
    priority     INT          NOT NULL DEFAULT 100,
    created_by   VARCHAR(16)  NOT NULL CHECK (created_by IN ('SYSTEM', 'USER')),
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT ux_merchant_rules_pattern UNIQUE (pattern, match_type),
    CONSTRAINT ck_merchant_rules_pattern_upper CHECK (pattern = upper(pattern))
);

CREATE INDEX ix_merchant_rules_category ON merchant_rules (category_id);

-- ---------------------------------------------------------------------
-- transactions: the "golden record". One row per real-world transaction,
-- no matter how many sources (SMS, email, statement) reported it.
-- ---------------------------------------------------------------------
CREATE TABLE transactions (
    id               BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    account_id       BIGINT       NOT NULL REFERENCES accounts (id),
    amount_paise     BIGINT       NOT NULL CHECK (amount_paise > 0),  -- money is never a float
    direction        VARCHAR(6)   NOT NULL CHECK (direction IN ('DEBIT', 'CREDIT')),
    merchant_raw     VARCHAR(256),                     -- exactly as the bank wrote it
    merchant_norm    VARCHAR(256),                     -- e.g. 'SWIGGY'; what rules match on
    reference_no     VARCHAR(64),                      -- UPI ref / auth code, normalized
    txn_time         TIMESTAMPTZ  NOT NULL,
    category_id      BIGINT       NOT NULL REFERENCES categories (id),
    category_source  VARCHAR(16)  NOT NULL
                     CHECK (category_source IN ('DEFAULT', 'RULE', 'USER', 'LLM')),
    source           VARCHAR(16)  NOT NULL CHECK (source IN ('SMS', 'EMAIL', 'STATEMENT')),
    raw_text         TEXT         NOT NULL,            -- copy of the first evidence, for convenience
    version          INT          NOT NULL DEFAULT 0,  -- JPA optimistic locking
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- Dedup layer 2: when a reference number exists, it is a hard identity.
-- Partial index: rows without a reference are not constrained at all.
CREATE UNIQUE INDEX ux_transactions_account_dir_ref
    ON transactions (account_id, direction, reference_no)
    WHERE reference_no IS NOT NULL;

-- Dedup layer 3 (fuzzy match: same account + amount, nearby time).
-- Column order matters: equality columns first, range column (txn_time) last.
CREATE INDEX ix_transactions_account_amount_time
    ON transactions (account_id, amount_paise, txn_time);

-- Monthly summaries / "recent transactions" scans by time.
CREATE INDEX ix_transactions_txn_time ON transactions (txn_time);

-- Postgres does NOT auto-index foreign key columns (MySQL does).
CREATE INDEX ix_transactions_category ON transactions (category_id);

-- ---------------------------------------------------------------------
-- raw_messages: every inbound message, parsed or not. Never deleted.
-- This is the evidence log; transactions are derived from it.
-- ---------------------------------------------------------------------
CREATE TABLE raw_messages (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    source          VARCHAR(16)  NOT NULL CHECK (source IN ('SMS', 'EMAIL', 'STATEMENT')),
    sender          VARCHAR(64)  NOT NULL,             -- e.g. 'AX-HDFCBK'
    body            TEXT         NOT NULL,
    received_at     TIMESTAMPTZ  NOT NULL,             -- as reported by the phone / mail server
    -- Dedup layer 1: sha256(source|sender|body) in hex. Excludes received_at
    -- on purpose, so a retried Shortcut call with a new timestamp still collides.
    content_hash    CHAR(64)     NOT NULL,
    parse_status    VARCHAR(16)  NOT NULL
                    CHECK (parse_status IN ('PARSED', 'UNPARSED', 'IGNORED', 'DUPLICATE')),
    parse_error     TEXT,
    transaction_id  BIGINT       REFERENCES transactions (id),
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT ux_raw_messages_content_hash UNIQUE (content_hash),
    -- A PARSED or DUPLICATE message must point at the transaction it produced/matched.
    CONSTRAINT ck_raw_messages_linked CHECK (
        parse_status NOT IN ('PARSED', 'DUPLICATE') OR transaction_id IS NOT NULL
    )
);

CREATE INDEX ix_raw_messages_transaction ON raw_messages (transaction_id);

-- Small partial index: the "unparsed inbox" query stays fast as raw_messages grows.
CREATE INDEX ix_raw_messages_unparsed
    ON raw_messages (created_at)
    WHERE parse_status = 'UNPARSED';

-- The name from the original spec, as a view over the single source of truth.
CREATE VIEW unparsed_messages AS
    SELECT id, source, sender, body, received_at, parse_error, created_at
    FROM raw_messages
    WHERE parse_status = 'UNPARSED';
