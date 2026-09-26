-- Shadow mode, drift monitoring (SPEC §10, §11, §14). Run by parallax_owner; ends with grants.

-- The single shadow-mode slot: at most one REPLAYED/PROPOSED version scores live traffic silently.
CREATE TABLE shadow_config (
    id         int PRIMARY KEY CHECK (id = 1),
    version    varchar(16),
    enabled_by varchar(128),
    enabled_at timestamptz
);
INSERT INTO shadow_config (id, version) VALUES (1, NULL);

-- One shadow evaluation per live DECISION/REDECISION ledger row (agrees = same outcome and limit).
CREATE TABLE shadow_result (
    ledger_seq   bigint PRIMARY KEY,
    version      varchar(16) NOT NULL,
    outcome      varchar(10),
    score        int,
    credit_limit int,
    agrees       boolean NOT NULL,
    created_at   timestamptz NOT NULL
);

-- One stored PSI drift report (SPEC §11): score distribution now vs the development baseline.
CREATE TABLE drift_report (
    id           bigserial PRIMARY KEY,
    created_at   timestamptz NOT NULL,
    as_of        date NOT NULL,
    live_version varchar(16) NOT NULL,
    baseline_n   int NOT NULL,
    current_n    int NOT NULL,
    bins         jsonb NOT NULL,
    psi          numeric(8,5) NOT NULL,
    status       varchar(12) NOT NULL
);

-- Least-privilege grants (SPEC §14): shadow_config is a singleton the runtime only toggles; the other
-- two are append-only reads/inserts. Re-grant sequence usage so drift_report_id_seq is reachable.
GRANT SELECT, UPDATE ON shadow_config TO parallax_app;
GRANT SELECT, INSERT ON shadow_result TO parallax_app;
GRANT SELECT, INSERT ON drift_report TO parallax_app;
GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA public TO parallax_app;
