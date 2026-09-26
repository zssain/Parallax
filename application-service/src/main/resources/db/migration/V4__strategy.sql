-- Strategy Lab tables (SPEC §10, §14). Run by parallax_owner; ends with explicit least-privilege grants.

-- Known loan outcomes for replay's expected-loss maths. simulated = true for reject-inference outcomes
-- (rows the lender never actually booked); false for observed outcomes on approved loans.
CREATE TABLE loan_outcome (
    application_id  bigint PRIMARY KEY REFERENCES application(id),
    defaulted       boolean NOT NULL,
    months_observed int NOT NULL DEFAULT 12,
    simulated       boolean NOT NULL
);

-- One Strategy Lab replay run and its stored impact report.
CREATE TABLE replay_job (
    id                    varchar(16) PRIMARY KEY,
    candidate_version     varchar(16) NOT NULL,
    baseline_version      varchar(16) NOT NULL,
    candidate_config_hash char(64) NOT NULL,
    status                varchar(12) NOT NULL,
    progress              int NOT NULL DEFAULT 0,
    total                 int,
    report                jsonb,
    load_ms               bigint,
    evaluate_ms           bigint,
    total_ms              bigint,
    created_by            varchar(128),
    created_at            timestamptz NOT NULL,
    finished_at           timestamptz,
    error                 text
);

-- Per-decision flips captured by a replay (capped at 5,000 per job).
CREATE TABLE replay_flip (
    job_id                varchar(16),
    ledger_seq            bigint,
    application_public_id  varchar(16),
    baseline_outcome      varchar(10),
    candidate_outcome     varchar(10),
    baseline_score        int,
    candidate_score       int,
    baseline_limit        int,
    candidate_limit       int,
    candidate_reasons     jsonb,
    observed              boolean,
    PRIMARY KEY (job_id, ledger_seq)
);

-- Least-privilege grants for the runtime role (SPEC §14): loan_outcome is insert/select only;
-- replay_job needs UPDATE for progress and results; replay_flip needs DELETE for re-run cleanup.
GRANT SELECT, INSERT ON loan_outcome TO parallax_app;
GRANT SELECT, INSERT, UPDATE ON replay_job TO parallax_app;
GRANT SELECT, INSERT, DELETE ON replay_flip TO parallax_app;
