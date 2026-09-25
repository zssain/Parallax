# Prompt 10 of 23 — Resilience: circuit breaker, retries, automatic re-decision

## Context

A credit bureau outage must degrade into the review queue, never an error page or a lost applicant. In the UI, the System screen has “Simulate bureau outage” and “Restore bureau & run re-decision job” buttons; the pipeline modal shows “circuit OPEN”; the decision detail shows a “Bureau unavailable” card and later a “re-decided” pill. This prompt adds Resilience4j around the bureau and decision calls, the ENGINE\_PENDING path (a v1 bug fix: v1 stored a 503 under the idempotency key forever), and the two scheduled jobs that finish interrupted applications.

## Session rules

1. `git log --oneline -3`, `./mvnw -q -B verify`, `docker compose up -d postgres`. Red → stop.
2. Read `AGENTS.md`, `docs/SPEC.md` §3 (7b, 7c), §6, §7, and `docs/DECISIONS.md`. Read the orchestration, `DecisionCommitService`, `BureauService`, `DecisionClient`.
3. Real output only. Keep state transitions in `ApplicationStateMachine`.

## Build (application-service)

### 1. Dependencies

`io.github.resilience4j:resilience4j-spring-boot3`, `spring-boot-starter-aop`.

### 2. Configuration (`application.yml`, all overridable)

```yaml
resilience4j:
  circuitbreaker:
    instances:
      bureau:
        slidingWindowType: COUNT_BASED
        slidingWindowSize: 10
        minimumNumberOfCalls: 5
        failureRateThreshold: 50
        slowCallDurationThreshold: 2s
        slowCallRateThreshold: 50
        waitDurationInOpenState: 30s
        permittedNumberOfCallsInHalfOpenState: 3
        automaticTransitionFromOpenToHalfOpenEnabled: true
  retry:
    instances:
      decision:
        maxAttempts: 3
        waitDuration: 200ms
        enableExponentialBackoff: true
        exponentialBackoffMultiplier: 2
        retryExceptions: [com.parallax.application.decision.DecisionUnavailableException]
```

### 3. Wiring

- `BureauService.pullFor` SOAP call wrapped by `@CircuitBreaker(name="bureau")`. `CallNotPermittedException` and every bureau failure → the §3 7b path (`commitBureauUnavailable`). Pipeline BUREAU detail “circuit OPEN” when the call was not permitted, else “bureau unavailable”; status WARN.
- `DecisionClient.evaluate` wrapped by `@Retry(name="decision")`. After retries fail → §3 7c: no ledger row; `engine_attempts = 1`; status ENGINE\_PENDING; key COMPLETED in one transaction with **202** body `{applicationId, status:"ENGINE_PENDING", pipeline}` (ENGINE step WARN “engine unavailable, queued for retry”, LEDGER\_COMMIT SKIPPED). Log a DECISIONS.md entry explaining why 202 and not 503.
- `BureauCircuitControl` bean exposing `state()`, `forceOpen()`, `close()` (via `CircuitBreakerRegistry`), used by Prompt 11's dev endpoint.

### 4. Scheduled jobs (`@EnableScheduling`)

Each job body is guarded by `SELECT pg_try_advisory_lock(<job key>)` / `pg_advisory_unlock` on one connection (constants: RedecisionJob 727275, EngineRetryJob 727276); if the lock is not acquired, skip this run. Each exposes `runOnce()` returning the list of processed application ids (tests and Prompt 11 call it directly).

- `RedecisionJob` (fixed delay `parallax.jobs.redecision-ms`, default 60000): if the bureau circuit is OPEN, return empty. Otherwise take BUREAU\_UNAVAILABLE applications **whose current outcome is still REFER with B01** (skip ones an underwriter already reviewed), oldest first, batches of 50. For each: decrypt the stored form fields needed for the bureau call, pull (through the breaker), derive features (velocity counted as of the **original** application time), evaluate, and in ONE transaction append a REDECISION row with `linkedSeq` = the B01 row's seq and set status DECIDED. Failures leave the application for the next run.
- `EngineRetryJob` (default 60000 ms): ENGINE\_PENDING applications with engine\_attempts < 3 → rebuild the input (the bureau pull is reused), evaluate; success → `commitDecision` (DECISION row) and DECIDED; failure → engine\_attempts + 1; reaching 3 → ENGINE\_FAILED\_MANUAL.
- Both jobs log counts only, never PII.

### 5. Polling support

`GET /api/v1/applications/{id}` must work for ENGINE\_PENDING applications with no ledger row yet: `current`, `base`, `breakdown`, `bureau` null, `trail` empty, `status` ENGINE\_PENDING. Document this in SPEC §15 (one sentence under the detail row).

## Tests

- `CircuitBreakerIT`: WireMock returns 503 five times → the breaker opens; the next application gets REFER/B01 with pipeline BUREAU “circuit OPEN” and no SOAP call (WireMock count unchanged).
- `RedecisionIT`: create two BUREAU\_UNAVAILABLE applications, restore WireMock, close the breaker, call `RedecisionJob.runOnce()` → two REDECISION rows each linked to its B01 row, status DECIDED, detail trail shows DECISION then REDECISION, ledger verify ok. An application already overridden by an underwriter (insert an OVERRIDE row via `LedgerWriter` in a transaction) is skipped.
- `EnginePendingIT`: the test `DecisionClient` fails → 202 ENGINE\_PENDING; replaying the same key returns the same 202 with `Idempotent-Replay: true`; the client polls detail and sees ENGINE\_PENDING; the client heals; `EngineRetryJob.runOnce()` → DECIDED with a DECISION row; detail shows the outcome.
- `EngineFailedManualIT`: three failed runs → ENGINE\_FAILED\_MANUAL.
- `RetryTest`: the decision retry makes 3 attempts with increasing delays (verify with a counting stub).
- `JobLockIT`: while one connection holds lock 727275, `runOnce()` returns empty.

## Definition of Done (real output)

1. `./mvnw -B verify` green.
2. Manual: run the stack; set the bureau fault to DOWN via `curl -XPOST localhost:8082/admin/fault ...`; submit 5+ applications until one shows “circuit OPEN”; set NONE; wait for the job (or temporarily set `parallax.jobs.redecision-ms=5000`); paste the application detail's `trail` showing DECISION (B01) then REDECISION.
3. Update SPEC §15 as noted. Tick 10. Commit `PX-10: resilience, engine pending path, re-decision jobs`. REPORT.

## Do not

Build the review queue or the system endpoints (Prompt 11).
