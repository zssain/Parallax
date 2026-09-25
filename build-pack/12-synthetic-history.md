# Prompt 12 of 23 — Synthetic history, seeding and the demo applications

## Context

The Strategy Lab replays history, drift compares score distributions over time, and Overview shows a 12-month approval trend. All three need realistic history with known loan outcomes. This prompt builds `data-generator` (a deterministic synthetic applicant generator with default outcomes driven by a logistic PD model), a seed profile that ingests it into the ledger as SEED rows with `loan_outcome`, and a demo seeder that pushes the 14 demo applicants of SPEC §16 through the **real** pipeline so the web UI starts in the same state as the prototype (APP-1041 Priya declined, APP-1053 with the prompt-injection address, and so on). It also adds the `parallax.as-of` date that keeps time windows working on any calendar date (a v1 fix).

## Session rules

1. `git log --oneline -3`, `./mvnw -q -B verify`, `docker compose up -d postgres`. Red → stop.
2. Read `AGENTS.md`, `docs/SPEC.md` §2 (`parallax.as-of`), §5, §14 (`loan_outcome`, `replay_job`, `replay_flip`), §16 (demo table), and `docs/DECISIONS.md`.
3. The generator formulas below are exact. Do not tune them to get nicer numbers. Report the numbers you get.
4. Real output only.

## Build

### 1. data-generator (plain Java 21, no Spring; library + CLI)

Package `com.parallax.generator`. Depends on parallax-engine only (for `EngineInput`, `PullType`).

- `record GeneratedApplicant(EngineInput input, double pd, boolean defaulted)`.
- `final class ApplicantGenerator` with `static GeneratedApplicant next(SplittableRandom r, double drift, int asOfYear)` implementing exactly (r() = `r.nextDouble()`, drawn in this order):
  - t = r(); tier = t < 0.32 ? 0 : t < 0.78 ? 1 : 2
  - util = clamp(r() × \[0.30, 0.62, 0.95\]\[tier\] + drift × 0.20 × r(), 0, 0.99), rounded half-up to 3 decimals
  - inq = min(8, floor(−ln(1 − r() × 0.999) × \[0.6, 1.5, 2.8\]\[tier\] + drift × r() × 1.5))
  - delq = r() < \[0.03, 0.14, 0.42\]\[tier\] ? (r() < 0.6 ? 1 : 2 + floor(r() × 2)) : 0
  - tradelines = 1 + floor(r() × \[18, 11, 7\]\[tier\]); fileAge = 6 + floor(r() × \[260, 150, 90\]\[tier\])
  - income = round((24000 + r() × r() × 150000 + \[30000, 8000, 0\]\[tier\]) / 1000) × 1000
  - housing = round((500 + r() × 1700) / 50) × 50; debt = round(r() × \[500, 700, 1100\]\[tier\] / 10) × 10
  - age = r() < 0.04 ? 18 + floor(r() × 3) : 21 + floor(r() × 50); birthYear = asOfYear − age
  - independentIncome = r() < 0.55; bureauConsent = true; addressMismatch = r() < 0.008
  - ssnIssuanceYear = birthYear + (r() < 0.002 ? −2 : 1 + floor(r() × 15)); deceased = r() < 0.001; velocity24h = r() < 0.004 ? 3 : 1; pullType HARD
  - dti = (housing + debt) × 12 / income
  - z = −4.6 + 2.6 × util + 0.22 × min(inq, 6) + 0.75 × min(delq, 3) + 1.0 × min(dti, 1.5) − 0.004 × min(fileAge, 240)
  - pd = 1 / (1 + e^−z); defaulted = r() < pd
  - (“r() × r()” means two separate draws multiplied.)
- `final class HistoryGenerator` with `Stream<HistoryRecord> generate(HistoryParams p)` where `HistoryParams(int count, long seed, int days, double drift, int driftDays, LocalDate asOf)` and `record HistoryRecord(int i, Instant createdAt, GeneratedApplicant applicant)`. createdAt: record i (0-based, oldest first) = `asOf.atStartOfDay(UTC) − days + i × (days × 86400 / count)` seconds. drift for record i = p.drift if createdAt falls within the last driftDays before asOf, else 0. One `SplittableRandom(seed)` for the whole stream.
- CLI `Main`: `--count 100000 --seed 20260925 --out history.jsonl --days 365 --drift 0.35 --drift-days 60 --as-of 2026-09-25` (these are the defaults). Output one JSON line per record: `{"i":0,"createdAt":"…","input":{…EngineInput fields…},"pd":0.0123,"defaulted":false}` using a small hand-written JSON writer (no Jackson in this module). Print a summary: count, default rate, time taken.

### 2. As-of date — application-service `AsOfDate` bean

`LocalDate get()`: `parallax.as-of` if set; else the date of `max(created_at)` in decision\_ledger; else today (UTC). Not cached (one indexed query). Used by drift and trends (Prompt 15).

### 3. Tables — `V4__strategy.sql`

`loan_outcome`, `replay_job`, `replay_flip` exactly SPEC §14. Grants: loan\_outcome SELECT, INSERT; replay\_job SELECT, INSERT, UPDATE; replay\_flip SELECT, INSERT, DELETE.

### 4. History ingest — profile `seed`, `HistorySeeder`

- Runs when `parallax.seed.history-file` is set (or `parallax.seed.generate-count` > 0, which generates in-process with the defaults above, except as-of = today in UTC, so the history always ends on the day you seed and the drift windows contain data; application-service therefore depends on data-generator). Refuses unless `decision_ledger` has no row whose kind is other than GOVERNANCE (Prompt 14 adds a genesis GOVERNANCE row through a migration, and it must not block seeding), printing: “Seed refused: the ledger is not empty. To reseed, stop the stack and delete the Postgres volume (docker compose down -v).” Never deletes anything.
- For each record, in batches of 1,000 per transaction: insert an `application` (source SEED, name “Seed Applicant \<i>” encrypted and masked, SSN “990” + i zero-padded to 6 digits — fail if count > 1,000,000 — encrypted and tokenized, DOB = birthYear-01-15, email and phone null, address “Seed address \<i>”, the financials from the input, product cycling REWARDS/STORE/HEALTHCARE, status DECIDED, created\_at = record time); evaluate `DecisionEngine.evaluate(input, v1.3 config)` in-process; append a DECISION ledger row with source SEED and **createdAt = the record time** (add `LedgerWriter.appendAt(entry, Instant)` for seeds; same truncation rules), bureau\_pull\_id null; insert `loan_outcome(defaulted, 12, simulated = outcome != APPROVED)`.
- Set v1.3 `first_used_at`. Log progress every 10,000 records. At the end run `LedgerVerifier.verify()` and print the result.

### 5. Demo applications — `DemoApplicationsSeeder` (profile `seed`, runs after history)

- Requires bureau-mock and decision-service to be reachable; if not, fail with a clear message naming the URL.
- Submits the 14 rows of SPEC §16 in order through `ApplicationIntakeService` (the same code path as the API) as client `seed@parallax.dev` with idempotency keys `demo-1041` … `demo-1054`, applying the §16 rules for DOB, email, phone and address.
- Checks and logs expected results: 1041 DECLINED 445; 1043 REFER 655; 1047 DECLINED with P03; 1049 REFER with F01; 1051 REFER with F04; 1052 DECLINED with P01. A mismatch logs ERROR and the seeder exits non-zero.
- Then the seed application exits (`spring.main.web-application-type=none` in `application-seed.yml`; `SpringApplication.exit`).

### 6. Docs

README “Seeding”: the one-shot command (Postgres, bureau-mock and decision-service running):

```bash
./mvnw -q -pl application-service spring-boot:run -Dspring-boot.run.profiles=dev,seed -Dspring-boot.run.arguments=--parallax.seed.generate-count=20000
```

and the 100k variant (`--parallax.seed.generate-count=100000`).

## Tests

- `ApplicantGeneratorTest`: determinism (same seed → identical 1,000 applicants); every input is a valid `EngineInput`.
- `HistoryGeneratorTest`: same params → identical JSONL bytes (write twice, compare); createdAt strictly increasing; the last 60 days have drift.
- `DistributionSanityTest`: 20,000 records under v1.3 — print the approval rate, default rate and default rate among approvals; assert approval rate between 0.15 and 0.90 and default rate among approvals < overall default rate (the score must separate risk). Paste the printed numbers in your report.
- `HistorySeederIT` (2,000 records): 2,000 SEED applications and ledger rows, loan\_outcome rows = 2,000, `simulated=false` count = number of APPROVED rows, verify ok, second run refused with the message.
- `DemoApplicationsSeederIT`: with a test `BureauClient` implementing the SPEC §8 digit mapping and the in-process `DecisionClient` → APP-1041 … APP-1054 created with the six expected results above.
- `AsOfDateIT`: property set wins; else max ledger date; else today.

## Definition of Done (real output)

1. `./mvnw -B verify` green.
2. On the dev database (reset with `docker compose down -v && docker compose up -d postgres` if needed), run bureau-mock and decision-service, then the seed command with 20,000. Paste the seeder log summary, the verify result, and `GET /api/v1/applications?source=LIVE` (14 items) as aditi.
3. Tick 12. Commit `PX-12: synthetic history generator, seed ingest, demo applications, as-of date`. REPORT including the DistributionSanityTest numbers.

## Do not

Build replay (Prompt 13) or change engine rules to make numbers look better.
