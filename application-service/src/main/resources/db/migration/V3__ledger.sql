-- Append-only, hash-chained decision ledger (SPEC §5, §14). Run by parallax_owner.
CREATE TABLE decision_ledger (
    seq               bigint PRIMARY KEY,
    kind              varchar(12) NOT NULL,
    source            varchar(8) NOT NULL,
    application_id    bigint REFERENCES application(id),
    rule_version      varchar(16),
    scorecard_version varchar(16),
    engine_version    varchar(24),
    bureau_pull_id    varchar(16),
    bureau_reused     boolean,
    engine_input      jsonb,
    outcome           varchar(10),
    score             int,
    credit_limit      int,
    reason_codes      jsonb,
    fraud_flags       jsonb,
    atp_max           int,
    linked_seq        bigint,
    override_detail   jsonb,
    governance_detail jsonb,
    created_at        timestamptz NOT NULL,
    prev_hash         char(64) NOT NULL,
    hash              char(64) NOT NULL UNIQUE
);
CREATE INDEX idx_ledger_application ON decision_ledger (application_id, seq);
CREATE INDEX idx_ledger_created_at ON decision_ledger (created_at, seq);
CREATE INDEX idx_ledger_kind ON decision_ledger (kind, seq);

-- Append-only: the runtime role may only read and insert; never update, delete or truncate.
GRANT SELECT, INSERT ON decision_ledger TO parallax_app;
REVOKE UPDATE, DELETE, TRUNCATE ON decision_ledger FROM parallax_app;
