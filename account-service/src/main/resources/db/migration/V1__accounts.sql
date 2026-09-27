-- account-service schema (SPEC §14, database "accounts"). Flyway runs as accounts_owner; the runtime
-- connects as the least-privilege accounts_app, whose grants are listed at the end of this migration.
-- Money is in cents here (columns suffixed *_cents); credit_limit is whole dollars, matching the engine.

CREATE TABLE account (
    id              bigserial PRIMARY KEY,
    public_id       varchar(16) UNIQUE NOT NULL,
    application_id  varchar(16) UNIQUE NOT NULL,
    display_name    varchar(80) NOT NULL,
    product         varchar(24) NOT NULL,
    credit_limit    int NOT NULL,
    balance_cents   bigint NOT NULL DEFAULT 0,
    apr_bps         int NOT NULL DEFAULT 2499,
    opened_at       timestamptz NOT NULL,
    statement_clock date NOT NULL,
    status          varchar(16) NOT NULL,
    days_past_due   int NOT NULL DEFAULT 0,
    annual_income   int,
    monthly_housing int,
    monthly_debt    int
);

CREATE TABLE statement (
    id                    bigserial PRIMARY KEY,
    account_id            bigint NOT NULL REFERENCES account(id),
    period_end            date NOT NULL,
    closing_balance_cents bigint NOT NULL,
    interest_cents        bigint NOT NULL,
    minimum_due_cents     bigint NOT NULL,
    due_date              date NOT NULL,
    paid_cents            bigint NOT NULL DEFAULT 0,
    paid_on_time          boolean
);

CREATE TABLE card_transaction (
    id           bigserial PRIMARY KEY,
    account_id   bigint NOT NULL REFERENCES account(id),
    posted_at    date NOT NULL,
    type         varchar(12) NOT NULL,
    amount_cents bigint NOT NULL,
    description  varchar(120)
);

CREATE TABLE cli_request (
    id              bigserial PRIMARY KEY,
    account_id      bigint NOT NULL REFERENCES account(id),
    requested_limit int NOT NULL,
    outcome         varchar(16) NOT NULL,
    new_limit       int NOT NULL,
    reasons         jsonb NOT NULL,
    applied         boolean NOT NULL,
    created_by      varchar(128) NOT NULL,
    created_at      timestamptz NOT NULL
);

CREATE TABLE collection_action (
    id         bigserial PRIMARY KEY,
    account_id bigint NOT NULL REFERENCES account(id),
    type       varchar(24) NOT NULL,
    note       text,
    created_by varchar(128) NOT NULL,
    created_at timestamptz NOT NULL
);

CREATE INDEX idx_statement_account ON statement (account_id, period_end DESC);
CREATE INDEX idx_transaction_account ON card_transaction (account_id, posted_at DESC);

-- Public account ids start at ACC-88201 (SPEC §14).
CREATE SEQUENCE account_public_seq START 88201;

-- Least-privilege grants (SPEC §14): accounts and statements are read/insert/update (balances,
-- statement_clock, paid_cents, days_past_due, status all mutate); transactions, CLI requests and
-- collection actions are append-only.
GRANT SELECT, INSERT, UPDATE ON account TO accounts_app;
GRANT SELECT, INSERT, UPDATE ON statement TO accounts_app;
GRANT SELECT, INSERT ON card_transaction TO accounts_app;
GRANT SELECT, INSERT ON cli_request TO accounts_app;
GRANT SELECT, INSERT ON collection_action TO accounts_app;
GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA public TO accounts_app;
