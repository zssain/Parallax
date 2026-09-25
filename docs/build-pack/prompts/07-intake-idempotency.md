# Prompt 07 of 23 — Intake, idempotency, bureau client, engine input

## Context

Parallax's live flow (SPEC §3) starts with `POST /api/v1/applications`. In the web UI this is the **New application** screen: its Submit button sends one request, and its “Simulate double-click” button sends two concurrent requests with the same Idempotency-Key and shows both responses. This prompt builds everything up to a ready `EngineInput`: validation, concurrency-safe idempotency, encrypted persistence, the SOAP bureau client with its 30-day reuse window, derived fraud features (velocity, address mismatch, age) and server-measured pipeline timings. It stops at status BUREAU\_PULLED. Prompt 08 builds the ledger; Prompt 09 completes the decision.

## Session rules

1. `git log --oneline -3`, `./mvnw -q -B verify`, `docker compose up -d postgres`. Red → stop.
2. Read `AGENTS.md`, `docs/SPEC.md` §3, §6, §7, §8, §9, §14 and the `POST /api/v1/applications` row of §15, and `docs/DECISIONS.md`.
3. Reuse existing code: `EngineInput`/`PullType` (engine), JAXB classes (`com.parallax.bureau.contract`), `CanonicalJson`, `DataCipher`, `Tokenizer`, `NameMasker`, `ApiProblem`, `AbstractPostgresIT`, `SecurityConfig`. Do not redefine them.
4. Build exactly this. Real output only.

## Build (application-service)

### 1. Dependencies

Add `spring-boot-starter-web-services` (client side) and `bureau-contract`. Test: `org.wiremock:wiremock-standalone`.

### 2. Persistence

- `ApplicationEntity` (JPA) mapped to the §14 `application` table exactly; encrypted columns use `EncryptedStringConverter`; `public_id` = `"APP-" + nextval('application_public_seq')` assigned on insert via a repository method using JdbcTemplate for the sequence.
- `ApplicationStatus` enum: RECEIVED, BUREAU\_PULLED, BUREAU\_UNAVAILABLE, ENGINE\_PENDING, ENGINE\_FAILED\_MANUAL, DECIDED, REVIEWED; plus `ApplicationStateMachine.transition(from, to)` enforcing §6 (illegal → `IllegalStateException`). All status changes go through it.
- `IdempotencyKeyEntity`, `BureauPullEntity` mapped to §14.

### 3. Request contract

`ApplicationRequest` record with Bean Validation exactly as §15: firstName, lastName `@Size(1,60)`; dateOfBirth `@Past`; ssn `@Pattern("^9\\d{8}$")`; email optional `@Email`; phone optional, 10–15 digits after stripping non-digits (custom constraint); address `@Size(6,200)`; annualIncome 1–10,000,000; monthlyHousing and monthlyDebt 0–100,000; independentIncome, bureauConsent `@NotNull`; product enum `Product { REWARDS_CARD, STORE_CARD, HEALTHCARE_CARD }`. Missing or invalid `Idempotency-Key` header (1–128 chars) → 400 ProblemDetail with fieldErrors `[{field:"Idempotency-Key", …}]`.

### 4. Idempotency — `IdempotencyService` (exactly SPEC §7)

- `requestHash = sha256Hex(CanonicalJson.write(request))`.
- `begin(clientId, key, hash)` runs in `REQUIRES_NEW`: INSERT IN\_PROGRESS with `expires_at = now + 24h`. On unique violation read the row: COMPLETED + same hash → return `Replay(status, body)`; COMPLETED + different hash → `ApiProblem(422, "Idempotency-Key reused with a different request body")`; IN\_PROGRESS → `ApiProblem(409, "A request with this Idempotency-Key is still in progress")`; expired → delete and insert once more.
- `complete(clientId, key, status, body, applicationPublicId)` — in this prompt runs in its own `REQUIRES_NEW` transaction; Prompt 09 moves it into the decision transaction (leave a `// PX-9: move into decision transaction` comment).
- `abandon(clientId, key)` in `REQUIRES_NEW` deletes the key; called when processing throws.
- Controller: a Replay returns the stored body with header `Idempotent-Replay: true` and status 200 when the stored status was 201, else the stored status.

### 5. Bureau — `BureauClient` + `BureauService`

- `BureauClient`: `WebServiceTemplate` with a `Jaxb2Marshaller` for `com.parallax.bureau.contract`, default URI `parallax.bureau.url`, message sender with 2 s connect and 2 s read timeout. Method `CreditReportResponse pull(CreditReportRequest)`. Also return the marshalled response XML string (pretty-printed, as the UI shows it).
- `BureauService.pullFor(application, plaintextForm)`: if a `bureau_pull` exists with the same ssn\_token and pull type HARD and `pulled_at >= now - reuse-days`, reuse it (reused = true, no SOAP call). Otherwise call SOAP with pullType HARD, store `bureau_pull` (id = PullId, profile = ProfileLabel, attributes = JSON of the numeric fields and fileAddress, ssnIssuanceYear, deceased; raw\_xml = the response XML) and return it. Result record `BureauReport(pullId, pullType, reused, profile, fileAddress, ssnIssuanceYear, deceased, openTradelines, inquiries6m, delinquencies24m, revolvingUtilization, fileAgeMonths, rawXml)`.
- Any exception (timeout, SOAP fault, 5xx) → `BureauUnavailableException`. Circuit breaking arrives in Prompt 10.

### 6. Derived features and engine input — `FeatureService` + `EngineInputMapper`

- velocity24h: `SELECT count(*) FROM application WHERE created_at > :createdAt - interval '24 hours' AND created_at <= :createdAt AND (ssn_token = :t OR (:e IS NOT NULL AND email_hash = :e) OR (:p IS NOT NULL AND phone_hash = :p))` — includes the current application (already saved).
- addressMismatch: `normalize(a) != normalize(b)`, normalize = lowercase, remove `[^a-z0-9]`.
- age: full years between DOB and the application's `created_at` date in UTC; birthYear from the DOB.
- revolvingUtilization rounded half-up to 3 decimals. pullType from the report.

### 7. Pipeline timings — `PipelineRecorder`

A request-scoped recorder. A filter stores the request start time; VALIDATE = ms from filter start to controller entry. IDEMPOTENCY = the `begin` call. BUREAU = the bureau step (detail “report reused (window 30 d)”, “fresh pull”, or WARN “bureau unavailable”). FRAUD\_SCREEN = feature derivation (detail filled in Prompt 09). Items `{step, label, status, ms, detail}` with the §3 labels.

### 8. Orchestration for this prompt — `ApplicationIntakeService`

`begin key → save application RECEIVED (encrypt, tokenize, mask) → bureau → BUREAU_PULLED → features → EngineInput`. Respond **202** `{applicationId, status, pipeline, engineInputPreview}` and complete the key with that body. Bureau failure → status BUREAU\_UNAVAILABLE, 202 with that status, key completed. Any other exception → `abandon` the key and rethrow. Mark `engineInputPreview` with `// PX-9: remove`.

### 9. Security

Add to `SecurityConfig`: `POST /api/v1/applications` → CLIENT, UNDERWRITER, STRATEGIST, APPROVER. client\_id = the authenticated username.

## Tests (`*IT` extend `AbstractPostgresIT`; WireMock stubs `/ws` with SOAP responses rendered from the JAXB classes)

- Validation: missing Idempotency-Key → 400 with a fieldError; ssn `812345678` → 400 fieldError on `ssn`; phone “12” → 400.
- Happy path (SSN 912345678, PRIME stub) → 202 BUREAU\_PULLED; one application row; one bureau\_pull row; pipeline has 4 items in order.
- Reuse: a second application with the same SSN (new key) → no second SOAP call (WireMock verify count 1), BUREAU detail “report reused (window 30 d)”.
- Idempotency: same key + same body twice → second 200 with `Idempotent-Replay: true` and an identical body, still one application; same key + different body → 422; two concurrent requests with the same key (two threads, a `CountDownLatch`, the WireMock stub delayed 500 ms) → exactly one 202 and one 409.
- Failure: WireMock 500 → 202 BUREAU\_UNAVAILABLE; an exception injected after the key insert (test-only bean) → key row removed.
- Velocity: three applications sharing a phone within 24 h → the third has velocity24h 3; different SSNs, no email, no phone → 1.
- Address mismatch: NEAR\_PRIME/ADDRESS\_MISMATCH stub → addressMismatch true.
- Security: no auth → 401; AUDITOR → 403.
- PII: after a full request, captured logs contain no SSN, email or DOB; raw `ssn_enc` bytes (JdbcTemplate) do not contain the SSN digits.
- `ApplicationStateMachineTest`: every legal transition passes, three illegal ones throw.

## Definition of Done (real output)

1. `./mvnw -B verify` green (paste application-service totals).
2. Run Postgres, bureau-mock (dev) and application-service (dev); run:

```bash
KEY=$(uuidgen)
curl -s -u priya.menon@parallax.dev:demo-password -H "Idempotency-Key: $KEY" -H 'Content-Type: application/json' localhost:8080/api/v1/applications -d '{"firstName":"Ishaan","lastName":"Kapoor","dateOfBirth":"1996-04-18","ssn":"912345678","address":"48 Elm Street, Columbus OH","annualIncome":64000,"monthlyHousing":1350,"monthlyDebt":280,"independentIncome":true,"bureauConsent":true,"product":"REWARDS_CARD"}'
```

Paste the 202, then repeat with the same `$KEY` and paste the 200 with `Idempotent-Replay` (`curl -i`).

3. Tick 07. Commit `PX-7: intake, idempotency, bureau client, engine input`. REPORT.

## Do not

Call decision-service, write ledger rows or add circuit breakers.
