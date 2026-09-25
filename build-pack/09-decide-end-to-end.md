# Prompt 09 of 23 — Decide end to end: commit, read APIs, reproduce, adverse action

## Context

Prompts 07 and 08 built intake up to a ready `EngineInput` and an append-only hash-chained ledger. This prompt joins them: call decision-service, commit the decision and its ledger row **in one transaction**, return the final 201 response with real pipeline timings, and build the read APIs that power four UI screens: **Decisions** (filterable table), **Decision detail** (outcome banner, score gauge, reason codes, policy and fraud checks, scorecard breakdown, engine input, Reproduce button, bureau XML, ledger trail, adverse action notice), **Decision ledger** (table, KPIs, Verify chain, Attempt UPDATE, Tamper test) and the recent-decisions part of Overview.

## Session rules

1. `git log --oneline -3`, `./mvnw -q -B verify`, `docker compose up -d postgres`. Red → stop.
2. Read `AGENTS.md`, `docs/SPEC.md` §3, §5, §6, §9, and in §15 every row for `/api/v1/applications*`, `/api/v1/decisions/*`, `/api/v1/ledger*`, and `docs/DECISIONS.md`. Read the intake, idempotency, ledger and pipeline code, and the `// PX-9` markers.
3. Response shapes are exactly §15. Do not add or rename fields.
4. Real output only.

## Build (application-service)

### 1. Live rules and the decision client

- `LiveRuleService`: `LiveRule current()` → `{version, config, since}` from the LIVE `rule_version` (cached; `evict()` method for Prompt 14). `RuleConfig configOf(String version)` for reproduce.
- `DecisionClient`: `RestClient` to `parallax.decision.url` + `/internal/v1/evaluate`, header `X-Internal-Token`, 2 s connect/read timeouts. Returns `EvaluateResponse`. Throws `DecisionUnavailableException` on I/O errors and 5xx. (Retry and the ENGINE\_PENDING path arrive in Prompt 10; for now the exception abandons the key and returns 503 ProblemDetail.)

### 2. Transactional commit — `DecisionCommitService`

- `commitDecision(app, input, bureauReport, evaluateResponse, kind, linkedSeq, clientKey)` in ONE `@Transactional`: `LedgerWriter.append` (kind DECISION or REDECISION, source LIVE, every field) → application status → DECIDED via the state machine → `UPDATE rule_version SET first_used_at = now() WHERE version = ? AND first_used_at IS NULL` → if clientKey present, `IdempotencyService.completeInCurrentTransaction(...)` with the final 201 body (replace the Prompt 07 REQUIRES\_NEW completion; remove the `// PX-9` comment).
- `commitBureauUnavailable(app, partialInput, clientKey)` in ONE transaction: DECISION row with outcome REFER, reasonCodes \["B01"\], score null, creditLimit 0, atpMax null, engineInput = `PartialEngineInput` → status stays BUREAU\_UNAVAILABLE → key completed with the 201 body.
- A test-only hook bean (`@Profile("test-fault")`) can throw after the ledger insert, to prove atomicity.

### 3. The 201 response and pipeline

Remove the 202 and `engineInputPreview`. Final body exactly §15: `{applicationId, status, outcome, score, creditLimit, reasonCodes: [{code, description}] (applicant-facing only), ruleVersion, ledgerSeq, decidedAt, bureau: {pullId, reused} | null, pipeline}`. Pipeline: FRAUD\_SCREEN WARN with detail “n flag(s)” when fraudFlags is non-empty; ENGINE label “Decision engine · v1.3” (the actual version) with the measured call time; LEDGER\_COMMIT detail “seq #n” with the measured commit time. Bureau unavailable → FRAUD\_SCREEN and ENGINE are SKIPPED. Fraud flags never appear in this response.

### 4. Read model — `DecisionQueryService` + controllers

- Display name rule: decrypt `name_enc` for UNDERWRITER, STRATEGIST, APPROVER; `name_masked` for AUDITOR and ASSISTANT.
- **List** `GET /api/v1/applications`: the current (latest) ledger row per application via `DISTINCT ON (application_id) ... ORDER BY application_id, seq DESC`; filters outcome, q (public id prefix, case-insensitive), source (default LIVE); newest first by the current row's created\_at; `counts` over the same source and q (ignoring the outcome filter). Items exactly §15.
- **Detail** `GET /api/v1/applications/{id}`: base = latest non-OVERRIDE row; current = latest row. `breakdown` = re-run `DecisionEngine.evaluate(storedInput, configOf(base.ruleVersion))` and map policyChecks and scoreParts; `approveCutoff`, `referCutoff` from that config; `bandLimit` = the band limit for the score when APPROVED else null. For B01 rows `breakdown` and `bureau` are null. `ssnMasked` “\***-**-” + last4; `ssnEncPreview` from `DataCipher.preview`. `address` decrypted except null for AUDITOR. `untrustedTextFields: ["address"]`. `trail` = all rows for the application. `shadow` = null for now (Prompt 15 fills it). 404 ProblemDetail for an unknown id.
- **Reproduce** `GET /api/v1/decisions/{seq}/reproduce`: load the row, re-run under its stored version, compare outcome, score, creditLimit and reasonCodes; `identical` true/false; B01 rows → identical null, message “No engine evaluation (bureau unavailable)”. OVERRIDE rows reproduce their linked base row and say so in `message`.
- **Adverse action notice** `GET /api/v1/applications/{id}/adverse-action-notice` (text/plain): only when the current outcome is DECLINED and the base has at least one applicant-facing reason, else 404. A Java text block, template id AAN-v2, exactly:

```text
Notice of action taken · {decisionDate yyyy-MM-dd}

Dear {displayName},

Thank you for applying for the Parallax {productLabel}. After careful review, we are unable to approve your application at this time. The principal reasons for our decision are:

{numbered list of the base's applicant-facing reason descriptions}

Our decision was based in part on information from a consumer reporting agency: Parallax Mock Bureau (synthetic). The agency did not make this decision and cannot explain why it was made. You have the right to a free copy of your report within 60 days and to dispute its accuracy.

The federal Equal Credit Opportunity Act prohibits creditors from discriminating against credit applicants on a prohibited basis.

Template AAN-v2 · ledger #{baseSeq} · rules {ruleVersion}
```

productLabel: REWARDS\_CARD “Rewards Card”, STORE\_CARD “Store Card”, HEALTHCARE\_CARD “Healthcare Card”. No LLM, no randomness.

### 5. Ledger APIs — `LedgerController`

- `GET /api/v1/ledger?source=&page=&size=` (default ALL, 50, newest first): items §15; `note` = governance\_detail.note for GOVERNANCE rows, else null; displayName by the role rule.
- `GET /api/v1/ledger/stats`, `GET /api/v1/ledger/verify` (uses `LedgerVerifier`).
- Dev only: `POST /api/v1/ledger/demo/attempt-update` executes `UPDATE decision_ledger SET outcome='APPROVED' WHERE seq = (SELECT max(seq) FROM decision_ledger)` as parallax\_app and returns `{statement, error}` with the database error message (expected “permission denied for table decision\_ledger”). `POST /api/v1/ledger/demo/tamper-simulation` loads rows into memory (at most 50,000, newest), changes the middle row's credit\_limit to 25000 and outcome to APPROVED **in the copy only**, runs `LedgerVerifier.verify(copy)` and returns `{modifiedSeq, brokenAtSeq, checked}`. It never touches the table.

### 6. Security

Add the §15 roles for every endpoint above (INTERNAL for reads and demos; ASSISTANT only on `GET /api/v1/applications/{id}`).

## Tests (`*IT` with Testcontainers; WireMock for the bureau; for decision-service use a `@Primary` test `DecisionClient` that calls `DecisionEngine` in-process — document this in DECISIONS.md)

- `DecideHappyPathIT`: SSN 912345678 with Ishaan's financials (age 30) → 201 APPROVED, score 830, creditLimit 12000, ledgerSeq present, 6 pipeline items all OK; one ledger row; `verify` ok; rule v1.3 `first_used_at` set.
- `DeclineIT`: SSN 961234567 with Priya's financials (age 31) → DECLINED 445 with reasonCodes R22, R31, R14, R05; the notice contains those four descriptions, “Template AAN-v2” and no “F0”.
- `FraudReferIT`: SSN 937123456 with Sara's financials (45000/1200/300, age 28) → 201 REFER; the client body has no F01 anywhere; the detail (UNDERWRITER) shows fraudFlags \[F01\]; FRAUD\_SCREEN is WARN “1 flag(s)”.
- `BureauDownIT`: WireMock 503 → 201 REFER, reasonCodes \[\] (B01 is internal) and status BUREAU\_UNAVAILABLE; the ledger row has reason\_codes \["B01"\]; reproduce → identical null.
- `AtomicityIT` (profile test-fault): exception after the ledger insert → no ledger row, status not DECIDED, idempotency key removed, 500.
- `ReproduceIT`: reproduce identical true for every row created in these tests.
- `MaskingIT`: AUDITOR list and detail show masked names and null address; UNDERWRITER sees full names.
- `LedgerApiIT`: stats counts; attempt-update returns an error containing “permission denied”; tamper-simulation returns brokenAtSeq = modifiedSeq and the real `verify` stays ok.
- `GoldenReproduceIT`: 50 decisions across all six reference SSNs of SPEC §8 (vary financials) → all reproduce identical. Prompt 13 extends this to 500.

## Definition of Done (real output)

1. `./mvnw -B verify` green (paste totals).
2. Run Postgres, bureau-mock (dev), decision-service, application-service (dev). Paste real output of: an Ishaan application (APPROVED 830), a Priya application with SSN 961234567 (DECLINED 445), `GET /api/v1/applications/{priyaId}/adverse-action-notice`, `GET /api/v1/decisions/{seq}/reproduce`, `GET /api/v1/ledger/verify`.
3. Tick 09. Commit `PX-9: transactional decision commit, read APIs, reproduce, adverse action`. REPORT.

## Do not

Add resilience, the review queue or anything from the Strategy Lab.
