# Prompt 15 of 23 — Shadow mode, drift monitoring and the overview API

## Context

Three features complete the backend. **Shadow mode** lets a replayed candidate score live applications silently beside the live rules (the UI shows a “shadow” pill on the version card, a disagreements table in the Strategy Lab and a shadow block on the decision detail). **Drift monitoring** computes the Population Stability Index between the development baseline and recent applicants (the Drift screen shows grouped bars, PSI with stable / watch / investigate, a per-bin table, and a “Simulate a market shift” slider). **Overview** is the landing screen: five KPIs, a “Needs attention” list, an outcome-mix bar, a 12-month approval trend and recent decisions. After this prompt the backend is interview-ready.

## Session rules

1. `git log --oneline -3`, `./mvnw -q -B verify`, `docker compose up -d postgres`. Red → stop.
2. Read `AGENTS.md` (invariant 10: as-of date), `docs/SPEC.md` §10 (shadow), §11, §14 (shadow\_config, shadow\_result, drift\_report), and the shadow, drift and overview rows of §15; `docs/DECISIONS.md`. Read `DecisionCommitService`, `RuleVersionService`, `AsOfDate`, `ApplicantGenerator`.
3. Shapes exactly §15. Real output only.

## Build (application-service)

### 1. Tables — `V6__shadow_drift.sql`

`shadow_config` (insert the singleton row `id = 1`, version null), `shadow_result`, `drift_report` exactly §14. Grants: shadow\_config SELECT, UPDATE; shadow\_result SELECT, INSERT; drift\_report SELECT, INSERT.

### 2. Shadow mode

- `POST /api/v1/lab/versions/{v}/shadow` (STRATEGIST) `{enabled}`: only REPLAYED or PROPOSED versions (409 otherwise); enabling replaces any other shadow version; disabling clears it. Returns `{version, enabled}`.
- `DecisionCommitService` publishes `DecisionCommittedEvent(ledgerSeq, engineInput, liveDecision)` for LIVE DECISION and REDECISION rows that have engine data. A `@TransactionalEventListener(phase = AFTER_COMMIT)` reads shadow\_config; if set, evaluates `DecisionEngine` with that version's config in-process and inserts `shadow_result` (agrees = same outcome and same creditLimit). Any exception is logged (no PII) and swallowed.
- Approving (Prompt 14) a version that is the shadow version clears shadow\_config in the same transaction.
- `GET /api/v1/lab/versions/{v}/shadow-results` (INTERNAL): newest first, `{count, disagreements, items}`.
- Fill `shadow` in `GET /api/v1/applications/{id}` (from shadow\_result for the base seq) and in `GET /api/v1/lab/versions` items.

### 3. Drift — `DriftService`, `DriftController`, `DriftJob`

- Bins and PSI exactly §11. Baseline = SEED DECISION rows with `created_at < asOf − 90 days`; current = DECISION and REDECISION rows (any source) with `created_at > asOf − 30 days AND <= asOf + 1 day`; B01 rows excluded; every stored input re-scored under the **LIVE** config (score only). Baseline distribution cached per (live version, asOf).
- `run()` stores a drift\_report and returns it; `latest()` returns the newest; response `{id, createdAt, asOf, liveVersion, baselineN, currentN, bins [{from, to, baseline, current, contribution}], psi (5 decimals), status, simulated:false}`.
- `simulate(shift)` (0–1.5, else 422): application-service depends on `data-generator`; generate 5,000 applicants with `ApplicantGenerator.next(new SplittableRandom(4242), shift, asOfYear)`, score under LIVE, compare to the same baseline; return the same shape with `simulated: true`, `currentN: 5000`, `id: null`; never stored.
- `DriftJob`: cron `parallax.drift.cron` default `0 0 2 * * *` UTC, advisory-lock guarded (727277); also runs once at startup when no drift\_report exists and the ledger has SEED rows. WATCH or INVESTIGATE logs `WARN Drift alert psi=<value> status=<status>` (no PII).
- Endpoints: `GET /api/v1/drift/latest` (INTERNAL, ASSISTANT; 404 if none), `POST /api/v1/drift/run` (STRATEGIST, APPROVER), `POST /api/v1/drift/simulate` (INTERNAL).

### 4. Overview — `OverviewService`, `GET /api/v1/overview` (INTERNAL)

- Counts from the current row per LIVE-source application: `decisions`, `approved`, `refer`, `declined`, `approvalRate` (approved / decisions, 4 decimals).
- `reviewQueue` = the review queue length; `liveVersion {version, since}`; `psi {value, status}` from the latest report (null if none); `bureauCircuit`; `redecisionQueue` count; `proposedVersions` \[{version, proposedBy}\]; `shadowVersion`.
- `approvalTrend`: the 12 calendar months ending with the as-of month; per month, approved / total over DECISION and REDECISION rows (all sources, B01 excluded) created in that month; months with no rows → rate null.
- `recent`: the 7 newest current LIVE items (same shape as the Decisions list).
- `attention`, in this order and wording (only when the condition holds):
  - “{n} application(s) waiting in the review queue” → target `queue`, severity warn (singular “application” when n = 1, no “(s)”; plural “applications” otherwise)
  - for each PROPOSED version: “{v} proposed by {name} — needs a second approver” → `lab:{v}`, info
  - circuit OPEN: “Credit bureau circuit is OPEN — new applications fall back to REFER” → `system`, bad
  - re-decision queue > 0: “{n} bureau-outage REFER(s) waiting for automatic re-decision” (same singular/plural rule) → `system`, warn
  - always, when a drift report exists: “Score drift PSI {0.000} — {stable|watch|investigate}” → `drift`, ok / warn / bad
  - shadow active: “{v} is running in shadow mode on live traffic” → `lab:{v}`, acc

### 5. Security

Add the §15 roles for every endpoint above.

## Tests

- `ShadowIT`: enable shadow for a REPLAYED v1.4 (cutoff 700); an Ishaan application (830) agrees; a crafted 690-score approval disagrees (REFER in shadow); shadow-results count and disagreements are right; detail shows the shadow block; a forced shadow failure (test hook) leaves the 201 response unchanged.
- `ShadowApproveIT`: approving the shadow version clears shadow\_config.
- `PsiTest`: two crafted distributions with a hand-computed PSI → equal within 0.0001; thresholds 0.0999 / 0.10 / 0.25 / 0.2501 map to the right status.
- `DriftIT` (seed 3,000 with the default drift settings, as-of fixed): run → a stored report with baselineN and currentN > 0; latest returns it; simulate(0) and simulate(1.5) → the higher shift gives a higher PSI; neither is stored.
- `AsOfWindowIT`: with `parallax.as-of` two years in the future, current window is empty and status is computed without error (currentN 0 → PSI over floored proportions; document the behaviour).
- `OverviewIT`: on a crafted dataset the counts, attention messages (exact strings) and a 12-item trend are correct.

## Definition of Done (real output)

1. `./mvnw -B verify` green.
2. On the seeded dev DB paste: `POST /api/v1/drift/run`, `POST /api/v1/drift/simulate {"shift":1.2}` (PSI only), and `GET /api/v1/overview` as aditi.
3. Tick 15. Commit `PX-15: shadow mode, PSI drift with as-of windows, overview API`. REPORT.
4. Tell me: “The backend is complete through Prompt 15.”

## Do not

Build the assistant, the outbox or the UI.
