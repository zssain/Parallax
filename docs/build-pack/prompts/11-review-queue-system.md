# Prompt 11 of 23 — Review queue, overrides and system endpoints

## Context

REFER decisions (score band, fraud flags, bureau outage) need a human. In the UI, the **Review queue** screen lists them on the left (with a fraud / bureau / score pill), shows a decision panel on the right (Approve or Decline, credit limit capped at ability-to-pay, an override reason code, a required note, “Record decision”) and an “Override rate by band” table. Every override becomes its own OVERRIDE ledger row; the original decision is never modified. The **System** screen shows service health, the bureau circuit breaker with outage/restore buttons, the re-decision queue, recent idempotency keys and recent bureau pulls. This prompt builds the APIs for both screens.

## Session rules

1. `git log --oneline -3`, `./mvnw -q -B verify`, `docker compose up -d postgres`. Red → stop.
2. Read `AGENTS.md`, `docs/SPEC.md` §4 (override codes), §5, §6, and the `/api/v1/reviews/*` and `/api/v1/system/*` rows of §15, and `docs/DECISIONS.md`. Read `LedgerWriter`, `ApplicationStateMachine`, `RedecisionJob`, `BureauCircuitControl`.
3. Shapes exactly §15. Real output only.

## Build (application-service)

### 1. Review queue — `ReviewService`, `ReviewController`

- `OverrideCode` enum O1–O5 with the SPEC §4 descriptions.
- `GET /api/v1/reviews/queue` (INTERNAL): applications whose **current** ledger outcome is REFER and status is DECIDED or BUREAU\_UNAVAILABLE, plus ENGINE\_FAILED\_MANUAL applications; oldest first. `reasonKind`: `bureau` if the base has B01; `fraud` if the base has fraud flags; `engine` for ENGINE\_FAILED\_MANUAL; else `score`. `suggestedLimit` = min(atpMax, 2000), or 2000 when atpMax is null. displayName by the role rule.
- `POST /api/v1/reviews/{id}` (UNDERWRITER only), body `{decision, creditLimit?, overrideCode, note}`:
  - 409 unless the current outcome is REFER (or status ENGINE\_FAILED\_MANUAL).
  - 422 if note < 10 characters after trim (“Add a note of at least 10 characters explaining what you verified”); APPROVED requires creditLimit ≥ 300 (“Limit must be at least $300”) and ≤ the base atpMax when known (“Limit exceeds the ability-to-pay maximum of $n”); DECLINED forces creditLimit 0.
  - ONE transaction: append an OVERRIDE row (source LIVE; same engine\_input, rule\_version, scorecard\_version, engine\_version, bureau\_pull\_id, atp\_max, reason\_codes and fraud\_flags as the base; outcome = decision; credit\_limit; `linked_seq` = base seq; `override_detail` = `{by: displayName, username, code, codeDescription, note, linkedSeq}`), then status → REVIEWED.
  - ENGINE\_FAILED\_MANUAL has no base row: rebuild the EngineInput from the stored application and its reused bureau pull, append the OVERRIDE with that input, the LIVE rule version, score null, reason\_codes \[\], linked\_seq null. Add `ENGINE_FAILED_MANUAL → REVIEWED` to `ApplicationStateMachine` and one sentence to SPEC §6; detail for such an application returns `base` null and `current` = the override.
  - Response 201 `{applicationId, seq, outcome, creditLimit}`.
  - Log a DECISIONS.md line: an override to APPROVED will also open an account via the outbox (Prompt 18).
- `GET /api/v1/reviews/override-stats` (INTERNAL, ASSISTANT): bands “<620”, “620–679”, “680+”, “no score”. `refers` = DECISION/REDECISION rows with outcome REFER whose score falls in the band (null score → “no score”); `overriddenToApprove` = OVERRIDE rows with outcome APPROVED whose linked base row falls in the band; `rate` = overriddenToApprove / refers (0 when refers is 0), 4 decimals.

### 2. System endpoints — `SystemController`

- `GET /api/v1/system/status` (INTERNAL): `bureauCircuit` from `BureauCircuitControl`; `redecisionQueue` = the ids the re-decision job would pick (with display names); `enginePending`, `engineFailedManual` counts; `liveVersion`; `services` in this order with measured latency and a 1 s timeout each: application-service (self, UP), decision-service (`GET {decision.url}/actuator/health`), bureau-mock (`GET {bureau base}/actuator/health`), assistant-service (`GET {parallax.assistant.url, default http://localhost:8083}/actuator/health`), postgres (`SELECT 1`). DOWN on any error. Names exactly: “application-service”, “decision-service”, “bureau-mock (SOAP)”, “assistant-service”, “postgres”.
- Dev only, `POST /api/v1/system/bureau-fault` (INTERNAL) `{mode, delayMs?}`: POST the same body to bureau-mock `/admin/fault`. DOWN → `BureauCircuitControl.forceOpen()` so the next application falls back at once. SLOW → just the mock setting. NONE → `close()` the circuit, then call `RedecisionJob.runOnce()` synchronously. Returns `{mode, delayMs, circuit, redecided}`.
- `GET /api/v1/system/idempotency-keys` (INTERNAL): last 8 by created\_at, all clients, `{key, clientId, state, applicationId, createdAt, expiresAt}`.
- `GET /api/v1/system/bureau-pulls` (INTERNAL): last 8, `{pullId, pullType, profile, ssnLast4, pulledAt}`; ssnLast4 from the newest application with that ssn\_token.

### 3. Security

Add the §15 roles for every endpoint above.

## Tests

- `ReviewFlowIT`: a REFER (SSN 931xxxxxx, near-prime financials) appears in the queue with reasonKind `score`; UNDERWRITER approves with limit 1500, O2, a 20-char note → 201; ledger has an OVERRIDE row linked to the base; status REVIEWED; the queue no longer lists it; detail `current.kind` OVERRIDE with the override block; verify ok.
- Rules: note “ok” → 422; limit 200 → 422; limit above atpMax → 422 naming the max; reviewing an APPROVED application → 409; STRATEGIST POST → 403; reviewing twice → second is 409.
- Fraud and bureau kinds: SSN 937123456 → reasonKind `fraud`; a bureau-down application → `bureau`, and after an override the RedecisionJob skips it.
- `EngineFailedManualReviewIT`: an ENGINE\_FAILED\_MANUAL application can be declined with O3; OVERRIDE row with linked\_seq null; detail base null.
- `OverrideStatsIT`: a crafted set (2 refers in 620–679, one overridden to approve; 1 refer with no score) → exact counts and rates.
- `SystemIT`: status lists 5 services with the exact names (unreachable ones DOWN); bureau-fault DOWN (WireMock stands in for `/admin/fault`) → circuit OPEN and the next application REFER/B01; NONE → circuit CLOSED and that application re-decided in `redecided`.

## Definition of Done (real output)

1. `./mvnw -B verify` green.
2. With the stack running: submit a near-prime application, list the queue, approve it as priya.menon, paste the detail `trail`; call `GET /api/v1/system/status` and paste it.
3. Tick 11. Commit `PX-11: review queue, overrides, override stats, system endpoints`. REPORT.

## Do not

Touch the Strategy Lab, the outbox or the web app.
