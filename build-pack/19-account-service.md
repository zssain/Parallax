# Prompt 19 of 23 — account-service: accounts, statements, CLI, collections

## Context

`account-service` (port 8084, database `accounts`) completes the credit lifecycle. It receives `ACCOUNT_OPEN_REQUESTED` events from application-service's outbox (Prompt 18), keeps accounts, statements and transactions, decides credit line increases with the pure `CliPolicy`, tracks delinquency buckets and runs a collections work queue. The web app's **Accounts** screen (list + detail with a limit/balance summary, utilization bar, 12-month payment grid, transactions and a CLI request form) and **Collections** screen (bucket cards + a work queue with “Offer payment plan”) call it through the Vite proxy (Prompt 20).

## Session rules

1. `git log --oneline -3`, `./mvnw -q -B verify`, `docker compose up -d postgres`. Red → stop.
2. Read `AGENTS.md`, `docs/SPEC.md` §9, §13, §14 (accounts database) and the account-service lines of §15, `docs/DECISIONS.md`. Reuse the patterns from application-service (security, ProblemDetail handler, log masking, `AbstractPostgresIT`) — copy the small classes rather than creating a shared module.
3. Shapes exactly §15. Money in cents inside this service. Real output only.

## Build

### 1. Module

Create `account-service` (Spring Boot, package `com.parallax.account`, port 8084); add it to the parent POM, AGENTS.md module map and a commented docker-compose entry. Dependencies: web, validation, data-jpa, jdbc, security, actuator, flyway (+ postgresql module), postgresql, springdoc, parallax-engine. Tests: Testcontainers, WireMock.

### 2. Engine helper

Add `com.parallax.engine.scoring.Affordability.atpMax(int annualIncome, int monthlyHousing, int monthlyDebt, RuleConfig c)` with the exact SPEC §4 formula, and make `DecisionEngine` call it (no behaviour change; the Prompt 04 tests prove it). List this change in the report.

### 3. Database (`accounts`; Flyway as accounts\_owner, runtime accounts\_app)

`V1__accounts.sql`: the five §14 tables and `CREATE SEQUENCE account_public_seq START 88201`. Grants: account SELECT, INSERT, UPDATE; statement SELECT, INSERT, UPDATE; card\_transaction SELECT, INSERT; cli\_request SELECT, INSERT; collection\_action SELECT, INSERT; sequences USAGE, SELECT.

### 4. Opening accounts

`POST /internal/v1/accounts` (X-Internal-Token filter, no Basic auth): if an account exists for `applicationId` return 200 with it; else create `ACC-<seq>` with credit\_limit, display\_name, product, financials, balance 0, status CURRENT, opened\_at now, statement\_clock = today (UTC) → 201.

### 5. Simulated month — `StatementService.simulateMonth(accountId, purchasesCents, paymentCents, payOnTime)` (dev only endpoint)

In one transaction, in this order:

1. If purchasesCents > 0: PURCHASE transaction dated statement\_clock + 5 days; balance += purchases.
2. If paymentCents > 0: PAYMENT transaction dated statement\_clock + 20 days; balance −= payment. If a previous statement exists: its paid\_cents += payment and paid\_on\_time = payOnTime AND its paid\_cents ≥ minimum\_due\_cents.
3. If a previous statement exists and it was not paid on time (paid\_on\_time false or null) → days\_past\_due += 30; else days\_past\_due = 0.
4. period\_end = statement\_clock + 1 month. interest = balance > 0 ? round(balance × apr\_bps / 10000 / 12) : 0; if > 0 add an INTEREST transaction and balance += interest.
5. minimum\_due = balance ≤ 0 ? 0 : min(balance, max(2500, round(balance × 0.01) + interest)); due\_date = period\_end + 25 days; insert the statement.
6. status = days\_past\_due ≥ 30 ? DELINQUENT : CURRENT. statement\_clock = period\_end.

Returns the account detail. Endpoint `POST /api/v1/accounts/{id}/simulate-month` (UNDERWRITER, dev profile only).

### 6. Credit line increases

`POST /api/v1/accounts/{id}/cli-requests` (UNDERWRITER) `{requestedLimit, acceptCounterOffer}`: build `CliInput` — currentLimit; closedStatements = statement count; over the last 12 statements **excluding the newest** (its payment is not due yet): paymentsDueLast12 = count, onTimePaymentsLast12 = count with paid\_on\_time true; utilization = balance\_cents / (credit\_limit × 100); atpMax = `Affordability.atpMax` using the LIVE config from application-service `GET /internal/v1/rule-config/live` (RestClient with X-Internal-Token, cached 5 minutes); currentlyDelinquent = days\_past\_due > 0. Call `CliPolicy.evaluate`; store cli\_request (applied = APPROVED, or COUNTER\_OFFER with acceptCounterOffer); if applied, update credit\_limit. Response `{outcome, newLimit, reasons, applied}`.

### 7. Delinquency and collections

- `DelinquencyJob` (cron `parallax.delinquency.cron`, default daily 03:00 UTC; also `runOnce()`): recompute status from days\_past\_due (DELINQUENT ≥ 30, else CURRENT).
- Bucket of an account: 1–29, 30–59, 60–89, 90+ by days\_past\_due (0 = none).
- amountDueCents = newest statement minimum\_due\_cents + Σ over older statements not paid on time of max(0, minimum\_due\_cents − paid\_cents).
- `GET /api/v1/collections/summary` → the four buckets with count and amountDueCents (always all four, zero when empty).
- `GET /api/v1/collections?bucket=30-59` → accounts in the bucket (all buckets when omitted), ordered by days\_past\_due desc: `{accountId, displayName, daysPastDue, bucket, amountDueCents, balanceCents, lastContactAt, priority}`; priority per SPEC §13 (balance > $1,000 means balance\_cents > 100000).
- `POST /api/v1/collections/{accountId}/actions` (UNDERWRITER) `{type, note}` → 201.

### 8. Reads

`GET /api/v1/accounts?status=&page=&size=` and `GET /api/v1/accounts/{id}` exactly §15 (INTERNAL). utilization = balance / limit, 4 decimals.

### 9. Security and ops

HTTP Basic with the same four INTERNAL demo users; `/internal/v1/**` via the token filter; actuator health public; ProblemDetail errors; the PII log masking converter; Swagger in dev.

## Tests (`AbstractPostgresIT` against the `accounts` database; WireMock for application-service's internal endpoint)

- `OpenAccountIT`: POST twice with the same applicationId → 201 then 200, one row, public id ACC-88201.
- `StatementMathTest`: balance 150000 cents, APR 2499 → interest 3124; minimum = min(153124, max(2500, 1531 + 3124)) = 4655. Assert exact cents.
- `CliFlowIT`: open with limit 2000 and Ishaan's financials; simulate 6 months (purchases 20000 cents, payment = the previous statement's minimum due, on time; month 1 pays 0) → CLI request 3000 → APPROVED, limit 3000. Another account with one late month → CLI → DECLINED with “Account is past due”.
- `CollectionsIT`: two missed months → days\_past\_due 60, DELINQUENT, bucket 60–89, priority HIGH; summary counts match; an action sets lastContactAt.
- `SecurityIT`: AUDITOR can read, cannot POST cli-requests (403); wrong internal token → 401.
- End-to-end: with application-service's outbox pointing at this service (WireMock not used), `OutboxToAccountIT` is optional; the manual check below covers it.

## Definition of Done (real output)

1. `./mvnw -B verify` green.
2. Run Postgres, bureau-mock, decision-service, application-service and account-service (dev). Submit an approving application; within 10 s show `GET /api/v1/accounts` listing it. Simulate 6 on-time months (curl loop), request a CLI and paste the result; simulate a missed month on another account and paste `GET /api/v1/collections`.
3. Tick 19. Commit `PX-19: account service, statements, CLI, collections`. REPORT.

## Do not

Add a shared library module, message brokers, or UI.
