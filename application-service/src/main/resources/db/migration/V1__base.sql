-- Base schema for application-service (SPEC §14, database `parallax`).
-- Run by parallax_owner; ends with explicit grants so parallax_app stays least-privilege.

-- Public id sequence: "APP-" + nextval, starting at 1041 (the first demo application, SPEC §16).
CREATE SEQUENCE application_public_seq START 1041;

CREATE TABLE application (
    id                 bigserial PRIMARY KEY,
    public_id          varchar(16) UNIQUE NOT NULL,
    client_id          varchar(128) NOT NULL,
    name_enc           bytea NOT NULL,
    name_masked        varchar(80) NOT NULL,
    ssn_enc            bytea NOT NULL,
    ssn_token          char(64) NOT NULL,
    ssn_last4          char(4) NOT NULL,
    dob_enc            bytea NOT NULL,
    birth_year         int NOT NULL,
    email_enc          bytea,
    email_hash         char(64),
    phone_hash         char(64),
    address_enc        bytea NOT NULL,
    annual_income      int NOT NULL,
    monthly_housing    int NOT NULL,
    monthly_debt       int NOT NULL,
    independent_income boolean NOT NULL,
    bureau_consent     boolean NOT NULL,
    product            varchar(24) NOT NULL,
    status             varchar(24) NOT NULL,
    source             varchar(8) NOT NULL DEFAULT 'LIVE',
    engine_attempts    int NOT NULL DEFAULT 0,
    created_at         timestamptz NOT NULL,
    updated_at         timestamptz NOT NULL
);
CREATE INDEX idx_application_ssn_token ON application (ssn_token, created_at);
CREATE INDEX idx_application_email_hash ON application (email_hash, created_at);
CREATE INDEX idx_application_phone_hash ON application (phone_hash, created_at);
CREATE INDEX idx_application_status ON application (status);

CREATE TABLE idempotency_key (
    client_id             varchar(128) NOT NULL,
    idem_key              varchar(128) NOT NULL,
    request_hash          char(64) NOT NULL,
    state                 varchar(16) NOT NULL,
    response_status       int,
    response_body         jsonb,
    application_public_id varchar(16),
    created_at            timestamptz NOT NULL,
    expires_at            timestamptz NOT NULL,
    PRIMARY KEY (client_id, idem_key)
);

CREATE TABLE bureau_pull (
    id         varchar(16) PRIMARY KEY,
    ssn_token  char(64) NOT NULL,
    pull_type  varchar(8) NOT NULL,
    profile    varchar(16) NOT NULL,
    pulled_at  timestamptz NOT NULL,
    attributes jsonb NOT NULL,
    raw_xml    text NOT NULL
);
CREATE INDEX idx_bureau_pull_lookup ON bureau_pull (ssn_token, pull_type, pulled_at DESC);

CREATE TABLE rule_version (
    version        varchar(16) PRIMARY KEY,
    status         varchar(16) NOT NULL,
    config         jsonb NOT NULL,
    config_hash    char(64) NOT NULL,
    note           text,
    created_by     varchar(64) NOT NULL,
    created_at     timestamptz NOT NULL,
    proposed_by    varchar(64),
    approved_by    varchar(64),
    promoted_at    timestamptz,
    retired_at     timestamptz,
    first_used_at  timestamptz,
    rejection_note text
);

-- Least-privilege grants for the runtime role. No CREATE on the schema; no DELETE on append-only-ish
-- tables. bureau_pull is insert/select only; the decision ledger's INSERT/SELECT-only comes later.
GRANT SELECT, INSERT, UPDATE ON application TO parallax_app;
GRANT SELECT, INSERT, UPDATE, DELETE ON idempotency_key TO parallax_app;
GRANT SELECT, INSERT ON bureau_pull TO parallax_app;
GRANT SELECT, INSERT, UPDATE, DELETE ON rule_version TO parallax_app;
GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA public TO parallax_app;
