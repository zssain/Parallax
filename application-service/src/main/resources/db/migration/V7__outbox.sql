-- Transactional outbox (SPEC §13, §14): an APPROVED DECISION/REDECISION, or an OVERRIDE to APPROVED,
-- inserts an ACCOUNT_OPEN_REQUESTED event in the SAME transaction as the ledger row; a publisher posts
-- it to account-service (idempotent by applicationId). No Kafka, no distributed transaction. SEED rows
-- never create events.
CREATE TABLE outbox (
    id           bigserial PRIMARY KEY,
    event_type   varchar(40) NOT NULL,
    aggregate_id varchar(16) NOT NULL,
    payload      jsonb NOT NULL,
    created_at   timestamptz NOT NULL,
    published_at timestamptz,
    attempts     int NOT NULL DEFAULT 0,
    last_error   text
);

-- The publisher scans oldest-unpublished-first; a partial index keeps that scan cheap.
CREATE INDEX idx_outbox_unpublished ON outbox (id) WHERE published_at IS NULL;

-- Least-privilege grants (SPEC §14): the runtime inserts events, reads the pending batch, and updates
-- attempts / last_error / published_at. Re-grant sequence usage so outbox_id_seq is reachable.
GRANT SELECT, INSERT, UPDATE ON outbox TO parallax_app;
GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA public TO parallax_app;
