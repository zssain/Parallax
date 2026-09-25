# Prompt 18 of 23 — Credit-line-increase policy and the transactional outbox

## Context

Synchrony's Credit team decides accounts across the whole lifecycle: acquisition, account management and collections. Parallax covers acquisition so far. Phase 3 adds the rest: an approved decision opens an account in a separate `account-service` (Prompt 19), which runs statements, credit line increases (CLI) and collections. Two pieces come first and are built here: the **pure CLI policy** in `parallax-engine` (same purity rules as the decision engine), and the **transactional outbox** in application-service that reliably tells account-service to open an account, without a distributed transaction and without Kafka.

## Session rules

1. `git log --oneline -3`, `./mvnw -q -B verify`, `docker compose up -d postgres`. Red → stop.
2. Read `AGENTS.md`, `docs/SPEC.md` §13, §14 (`outbox`) and the `/internal/v1/*` rows of §15, `docs/DECISIONS.md`. Read `DecisionCommitService`, `ReviewService`, the purity test.
3. Real output only.

## Build

### 1. CLI policy — parallax-engine, package `com.parallax.engine.cli`

- `enum CliOutcome { APPROVED, COUNTER_OFFER, DECLINED }`
- `record CliInput(int currentLimit, int requestedLimit, int closedStatements, int onTimePaymentsLast12, int paymentsDueLast12, double utilization, int atpMax, boolean currentlyDelinquent)` with validation (non-negative, utilization 0–∞ allowed but not NaN, onTime ≤ due ≤ 12).
- `record CliDecision(CliOutcome outcome, int newLimit, List<String> reasons)`.
- `final class CliPolicy { static CliDecision evaluate(CliInput in) }` exactly SPEC §13, checks in this order, collecting every failed reason: currentlyDelinquent → “Account is past due”; closedStatements < 6 → “Account open less than 6 months”; paymentsDueLast12 ≥ 6 and onTimePaymentsLast12 < 10 × paymentsDueLast12 / 12 (i.e. the on-time ratio below 10/12) → “Fewer than 10 of 12 payments on time”; utilization > 0.90 → “Utilization above 90%”. Any failure → DECLINED with newLimit = currentLimit. Otherwise maxAllowed = floor(min(atpMax, 2 × currentLimit, 25000) / 100) × 100; requested ≤ maxAllowed → APPROVED at requested (reasons \[\]); else maxAllowed > currentLimit → COUNTER\_OFFER at maxAllowed (reasons \[“Requested limit exceeds what the account qualifies for”\]); else DECLINED at currentLimit with “Not eligible for a higher limit”.
- Tests: one example per branch; jqwik (1,000 tries): newLimit ≤ max(currentLimit, atpMax) and ≤ max(currentLimit, 2 × currentLimit); DECLINED ⇒ newLimit = currentLimit; APPROVED ⇒ newLimit = requested; determinism. `PurityArchTest` still passes.
- Record the exact on-time rule wording choice in DECISIONS.md.

### 2. Outbox — application-service

- `V7__outbox.sql`: the §14 `outbox` table; grants SELECT, INSERT, UPDATE.
- `OutboxWriter.accountOpenRequested(...)` (propagation MANDATORY) inserts event\_type `ACCOUNT_OPEN_REQUESTED`, aggregate\_id = application public id, payload `{applicationId, displayName (decrypted full name), product, creditLimit, annualIncome, monthlyHousing, monthlyDebt, ruleVersion, ledgerSeq}`.
- Call it inside the existing transactions: `DecisionCommitService` for LIVE DECISION/REDECISION rows with outcome APPROVED; `ReviewService` for OVERRIDE to APPROVED. Never for SEED rows.
- `OutboxPublisher` (`@Scheduled` fixed delay `parallax.outbox.publish-ms` default 5000; advisory-lock guarded, key 727278): oldest unpublished first, batches of 50, attempts < 10; POST the payload to `{parallax.accounts.url}/internal/v1/accounts` with `X-Internal-Token`, 2 s timeouts. 2xx → published\_at = now. Otherwise attempts + 1 and last\_error; when attempts reaches 10 log ERROR once (no PII). Expose `runOnce()`.
- Internal endpoint for account-service: `GET /internal/v1/rule-config/live` protected by the `X-Internal-Token` filter (not Basic auth) → `{version, config}`.

### 3. Security

`/internal/v1/**` bypasses Basic auth but requires the token filter; add a test that a wrong token gets 401.

## Tests

- `OutboxIT` (WireMock stands in for account-service): an approved application creates exactly one outbox row in the same transaction (the atomicity hook from Prompt 09 rolls back both); `runOnce()` posts it once and sets published\_at; a second `runOnce()` posts nothing; a 500 response increments attempts; a DECLINED decision creates no row; an override to APPROVED creates one; seeding 100 SEED rows creates none.
- `InternalRuleConfigIT`: correct token → LIVE config; wrong or missing token → 401.
- `CliPolicyTest`, `CliPolicyPropertiesTest` as above.

## Definition of Done (real output)

1. `./mvnw -B verify` green.
2. Paste one outbox row (`psql ... -c 'select id, event_type, aggregate_id, attempts, published_at from outbox'`) after submitting an approving application with account-service absent (attempts climbing is expected until Prompt 19).
3. Tick 18. Commit `PX-18: pure CLI policy, transactional outbox, internal rule config`. REPORT.

## Do not

Create account-service yet (Prompt 19).
