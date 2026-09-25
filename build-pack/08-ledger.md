# Prompt 08 of 23 — Decision service and the hash-chained ledger

## Context

This prompt builds “Record”, the part of Parallax that proves the design: an append-only, hash-chained `decision_ledger` that stores every decision with its exact typed input, so any decision can be reproduced years later and any tampering is detectable at the exact row. The UI's Decision ledger screen shows each row's `prev → hash`, a “Verify chain” button, an “Attempt UPDATE” demo and a “Tamper test”. It also builds `decision-service`, the thin REST wrapper that runs the engine for live decisions. **v1 of this project had a real bug here**: hashes computed before insert did not match after reading rows back (Java `Instant` nanoseconds vs Postgres microseconds; raw jsonb text). SPEC §5 fixes it; follow it exactly.

## Session rules

1. `git log --oneline -3`, `./mvnw -q -B verify`, `docker compose up -d postgres`. Red → stop.
2. Read `AGENTS.md`, `docs/SPEC.md` §4, §5, §14 (`decision_ledger`) and the decision-service line of §15, and `docs/DECISIONS.md`.
3. Reuse `CanonicalJson`, `AbstractPostgresIT`, engine types.
4. Build exactly this. Real output only.

## Build

### 1. Shared evaluate contract — parallax-engine, package `com.parallax.engine.api`

Plain records (no annotations): `EvaluateRequest(String ruleVersion, RuleConfig config, EngineInput input)` and `EvaluateResponse(String ruleVersion, String engineVersion, String scorecardVersion, Decision decision)`. Confirm the parent POM compiles with `-parameters` (spring-boot-starter-parent does) so Jackson can bind records; add a JSON round-trip test in decision-service proving it.

### 2. decision-service (port 8081)

- Dependencies: web, validation, actuator, parallax-engine.
- `POST /internal/v1/evaluate`: a filter checks header `X-Internal-Token` equals `parallax.internal-token` (env `INTERNAL_TOKEN`, dev default `internal-dev`) → else 401 ProblemDetail. Body `EvaluateRequest`. `RuleConfigValidator.validate(config)` non-empty → 422 ProblemDetail with `errors: [..]`. An `IllegalArgumentException` from `EngineInput` → 400. Otherwise 200 `EvaluateResponse` with `EngineVersion.VALUE` and `ScorecardVersion.VALUE`.
- Stateless, no database. Enums serialize by name.
- Tests: Priya and Ishaan through MockMvc match `DecisionEngine.evaluate` exactly (outcome, score, limit, reasons, atpMax); invalid config → 422 listing the error; missing token → 401; utilization 1.5 → 400.

### 3. Ledger table — application-service `V3__ledger.sql`

`decision_ledger` exactly as SPEC §14 with its three indexes. Then:

```sql
GRANT SELECT, INSERT ON decision_ledger TO parallax_app;
REVOKE UPDATE, DELETE, TRUNCATE ON decision_ledger FROM parallax_app;
```

### 4. Ledger code — package `com.parallax.application.ledger`

- `LedgerKind { DECISION, REDECISION, OVERRIDE, GOVERNANCE }`, `LedgerSource { LIVE, SEED }`.
- `PartialEngineInput` record for bureau-unavailable rows: `(int age, int birthYear, int annualIncome, int monthlyHousing, int monthlyDebt, boolean independentIncome, boolean bureauConsent, PullType pullType, String bureau)` where bureau is always “UNAVAILABLE”.
- `LedgerEntry` record (input to the writer): kind, source, applicationDbId, applicationPublicId, ruleVersion, scorecardVersion, engineVersion, bureauPullId, bureauReused, engineInput (an `Object` that is either `EngineInput` or `PartialEngineInput`, or null for GOVERNANCE), outcome, score (Integer), creditLimit (Integer), reasonCodes (List\<String>), fraudFlags (List\<String>), atpMax (Integer), linkedSeq (Long), overrideDetail (Map), governanceDetail (Map).
- `LedgerRecord` record (a stored row, all columns).
- `LedgerCanonicalizer.payload(LedgerRecord minus hash)` → the canonical JSON of exactly the 21 keys in SPEC §5, sorted, nulls included. `createdAt` formatted with `DateTimeFormatter.ofPattern("uuuu-MM-dd'T'HH:mm:ss.SSSSSS'Z'").withZone(UTC)` — always six fraction digits. `engineInput` written from the typed record. `hash = sha256Hex(prevHash + "|" + payload)`.
- `LedgerWriter.append(LedgerEntry)` with `@Transactional(propagation = MANDATORY)`:
  1. `SELECT pg_advisory_xact_lock(727274)` (constant `LEDGER_LOCK_KEY` with a comment).
  2. `SELECT seq, hash FROM decision_ledger ORDER BY seq DESC LIMIT 1`.
  3. seq = last + 1 or 1; prevHash = last hash or 64 zeros.
  4. `createdAt = Instant.now().truncatedTo(ChronoUnit.MICROS)` (a `Clock` bean, so tests can fix time).
  5. Build the record, compute the hash, INSERT with JdbcTemplate (jsonb columns via `?::jsonb`). Return the `LedgerRecord`.
- `LedgerReader`: `rowMapper` that parses `engine_input` back into `EngineInput` or `PartialEngineInput` (the latter when it has `"bureau":"UNAVAILABLE"`) and every other column into `LedgerRecord`.
- `LedgerVerifier.verify()` in a read-only transaction: stream rows ordered by seq with fetch size 5000; for each row check `prev_hash` equals the previous row's hash (genesis for the first) and recompute the hash from the parsed record; return `VerifyResult(ok, checked, brokenAtSeq, ms)`. Also `verify(List<LedgerRecord>)` for in-memory copies (used by the tamper demo later).

## Tests (`*IT` extend `AbstractPostgresIT`; insert parent `application` rows with a small test fixture)

- `LedgerRoundTripIT`: append a DECISION with an EngineInput whose utilization is 0.08 and 0.555, commit, read it back, recompute the hash → equal. Repeat with a PartialEngineInput and a GOVERNANCE row (null input). This is the regression test for the v1 bug; name it so.
- `LedgerChainIT`: 3 appends → seqs 1, 2, 3; row 1 prev = 64 zeros; each prev = previous hash; `verify()` ok with checked 3.
- `LedgerConcurrencyIT`: 20 threads each append in its own transaction → seqs 1..20 contiguous, verify ok.
- `LedgerGrantsIT`: as parallax\_app, `UPDATE decision_ledger SET outcome='APPROVED'` fails with SQLState 42501 (permission denied); `DELETE` likewise.
- `LedgerTamperIT`: as **parallax\_owner** (a separate DataSource), update one row's `credit_limit` → `verify()` returns ok false and brokenAtSeq = that row.
- `LedgerWriterIT`: calling `append` outside a transaction throws `IllegalTransactionStateException`.
- `LedgerCanonicalizerTest`: key order and a fixed expected JSON string for one hand-built record (write the expected string in the test).

## Definition of Done (real output)

1. `./mvnw -B verify` green (paste totals for decision-service and application-service).
2. Run decision-service (dev) and paste: `curl -s -H 'X-Internal-Token: internal-dev' -H 'Content-Type: application/json' localhost:8081/internal/v1/evaluate -d @docs/examples/evaluate-ishaan.json` (create that example file with Ishaan's input and the v1.3 config) → APPROVED 830.
3. Tick 08. Commit `PX-8: decision service, hash-chained append-only ledger`. REPORT.

## Do not

Wire the ledger into intake yet (Prompt 09) or add any ledger UPDATE/DELETE path.
