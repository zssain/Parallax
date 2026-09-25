# ADR 0004 — An as-of date for time windows

Status: Accepted · 2026-09-25

## Context

Drift (PSI) and approval trends are computed over time windows — "the last 30 days", "the last 12 months". Parallax runs on synthetic seed data that is fixed in time. If those windows were measured from a bare `now()`, a demo would show rich signals the day the data was generated and empty charts a month later, and results would silently change with the wall clock.

## Decision

All time-window logic uses a configured as-of date, `parallax.as-of`, never a bare `now()`. It defaults to the latest `decision_ledger.created_at` date (or today if the ledger is empty). Drift baseline = SEED rows older than as-of − 90 days; current = DECISION and REDECISION rows in (as-of − 30 days, as-of]; approval trend spans the 12 months ending at the as-of month.

## Consequences

- Demos and tests produce the same windows on any calendar date; drift and trend charts stay populated.
- The window logic is deterministic and unit-testable — pass an as-of, assert the buckets.
- One rule to remember (an AGENTS.md invariant): reach for `parallax.as-of`, not `Instant.now()`, in any windowed query.
- Live ingestion still advances naturally, because the default tracks the newest ledger row.
