# Prompt 13 of 23 — Strategy Lab replay and the impact report

## Context

This is Parallax's headline feature. A strategist drafts a candidate rule version (e.g. approve cutoff 680 → 700), then replays every historical decision through it. The UI's Strategy Lab shows a progress bar while it runs, then a report: four KPIs (approval rate before → after, decisions flipped, expected loss observed, outcome unknown), a “Simulated outcomes” toggle clearly labelled SIMULATION, a 3×3 flip matrix, a segment-impact table by score band, and a flipped-applicants table that opens a live-vs-candidate comparison. The report must be **honest**: loss is PD × EAD × LGD over observed outcomes only, and applicants a looser rule newly approves are “outcome unknown” (reject inference), never counted as safe. This prompt builds the replay job, the report and its read APIs. Prompt 14 builds the version lifecycle around it.

## Session rules

1. `git log --oneline -3`, `./mvnw -q -B verify`, `docker compose up -d postgres`. Red → stop.
2. Read `AGENTS.md`, `docs/SPEC.md` §10 and the `/api/v1/lab/versions/{v}/replays` and `/api/v1/lab/replays/*` rows of §15, and `docs/DECISIONS.md`. Read `LedgerReader`, `LiveRuleService`, the seed code and `DecisionEngine`.
3. Measure, never invent, every timing. Real output only.

## Build (application-service, package `com.parallax.application.lab`)

### 1. Loading

`ReplayLoader` streams replayable rows with JdbcTemplate and keyset pagination, 10,000 per page, fetch size 5,000, inside a read-only transaction:

```sql
SELECT l.seq, l.engine_input, l.outcome AS recorded_outcome, a.public_id, o.defaulted, o.simulated
FROM decision_ledger l
JOIN application a ON a.id = l.application_id
LEFT JOIN loan_outcome o ON o.application_id = l.application_id
WHERE l.kind IN ('DECISION','REDECISION')
  AND l.engine_input->>'bureau' IS DISTINCT FROM 'UNAVAILABLE'
  AND l.seq > ?
ORDER BY l.seq LIMIT 10000
```

Parse engine\_input into `EngineInput`. Time loading separately (loadMs).

### 2. Evaluation and accumulation

- Baseline = the **current LIVE** config; candidate = the version's config. Each page is split into chunks evaluated in parallel on a bounded `ExecutorService` sized to `Runtime.availableProcessors()`; each chunk fills its own `ReportAccumulator`; chunks are merged **in chunk order** so results are deterministic. Time evaluation separately (evaluateMs).
- Per record: n++; approvals; matrix\[baseline\]\[candidate\] over APPROVED, REFER, DECLINED; limitChanges when both APPROVED with different limits; exposure per side = limit × that side's ccf.
- Outcome classes: **observed** = a loan\_outcome row with simulated = false; **unknown** = no observed outcome AND the recorded outcome was not APPROVED (the lender never booked it); **immature** = no loan\_outcome AND the recorded outcome was APPROVED (recent live approvals without 12 months of history).
- expectedLossObserved per side = Σ over that side's approvals with an observed outcome of defaulted × limit × ccf × lgd.
- outcomeUnknown = {count, exposure} over candidate approvals classed unknown. immature = {count} over candidate approvals classed immature. Add `immature` to SPEC §10 (one sentence).
- expectedLossSimulated (candidate) = expectedLossObserved + Σ over unknown candidate approvals whose loan\_outcome is simulated of defaulted × limit × ccf × lgd.
- Segments by the **baseline** score via `Segments.scoreBand`: n, baselineApprovals, candidateApprovals, baselineLoss, candidateLoss (observed only).
- Flips: every record whose outcome differs → a `replay_flip` row (with candidate reason codes) up to 5,000; beyond that set flipsCapped true.

### 3. Job — `ReplayService`, `ReplayJobRunner`

- `POST /api/v1/lab/versions/{v}/replays` (STRATEGIST) body `{from?, to?}` (optional ISO instants filtering l.created\_at): the version must exist with status DRAFT or REPLAYED and a valid config (422 with errors); 409 if any job is QUEUED or RUNNING. Insert replay\_job QUEUED (id “RJ-” + 6 uppercase hex), submit to a dedicated `ThreadPoolTaskExecutor` “replay” (core 1, max 1, queue 5), return 202 `{jobId}`.
- ASSISTANT on the same endpoint: never starts work. Return 202 `{jobId}` of the newest DONE job whose `candidate_config_hash` equals the version's current config\_hash; 404 ProblemDetail “No replay exists for this version” otherwise.
- Runner: status RUNNING; total = a count query; after each page UPDATE progress; on success store report JSON, timings, finished\_at, status DONE; if the version is still DRAFT and its config\_hash equals the job's, set it to REPLAYED. On error: FAILED with the message.
- Report JSON (document the schema in SPEC §10):

```json
{"n":0,"baseline":{"version":"v1.3","approvals":0,"approvalRate":0,"exposure":0,"expectedLossObserved":0},
 "candidate":{"version":"v1.4","approvals":0,"approvalRate":0,"exposure":0,"expectedLossObserved":0,"expectedLossSimulated":0},
 "matrix":[[0,0,0],[0,0,0],[0,0,0]],"flips":0,"flipsCapped":false,"limitChanges":0,
 "outcomeUnknown":{"count":0,"exposure":0},"immature":{"count":0},
 "segments":[{"band":"<620","n":0,"baselineApprovals":0,"candidateApprovals":0,"baselineLoss":0,"candidateLoss":0}],
 "assumptions":{"ccf":0.6,"lgd":0.9,"pdSource":"observed synthetic outcomes"},
 "labels":{"simulatedIsSimulation":true}}
```

### 4. Read APIs

- `GET /api/v1/lab/replays/{jobId}` (INTERNAL, ASSISTANT): §15 shape.
- `GET /api/v1/lab/replays/{jobId}/flips?page=&size=` (INTERNAL): ordered by seq; `candidateReasons` from replay\_flip.
- `GET /api/v1/lab/replays/{jobId}/flips/{seq}` (INTERNAL): load the ledger row, re-run both configs, return the stored input, `observed`, and for each side `{version, approveCutoff, referCutoff, decision}` (full Decision including scoreParts and reasons).

### 5. Golden set and performance

- Extend `GoldenReproduceIT`: generate and seed 5,000 records in the test (same generator), then reproduce every row with `seq % 10 == 0` (500 rows) → all identical. Document the choice in DECISIONS.md.
- Performance (measure): on your local dev database seed **100,000** records (reset the volume first), then replay a candidate equal to v1.3 with approveCutoff 700. Record the actual loadMs, evaluateMs and totalMs and the machine (`sysctl -n machdep.cpu.brand_string`, `sysctl -n hw.ncpu`, `sysctl -n hw.memsize`). Write them in a README “Performance” section with the date. If 100k cannot run, run what you can and say so. Afterwards reset and reseed 20,000 for normal development.

## Tests (insert candidate rule\_version rows directly in fixtures; Prompt 14 adds the API)

- `ReplayTighteningIT` (seed 2,000): candidate approveCutoff 700 → candidate approvals ≤ baseline; outcomeUnknown.count 0; matrix total = n; every flip is APPROVED → REFER or DECLINED; version becomes REPLAYED.
- `ReplayLooseningIT`: candidate approveCutoff 660 → outcomeUnknown.count > 0; expectedLossObserved excludes those records; expectedLossSimulated ≥ expectedLossObserved.
- `ExpectedLossTest`: three hand-built records with known limits and outcomes → exact expected loss to the cent.
- `ReplayDeterminismIT`: two replays of the same candidate → identical report JSON (ignoring timings).
- `ReplayConcurrencyIT`: a second POST while one runs → 409.
- `ReplayAssistantIT`: as ASSISTANT, no DONE job → 404; after one completes → 202 with that jobId and no new job row.
- `FlipDetailIT`: flips page and flip detail return both decisions and correct cutoffs.

## Definition of Done (real output)

1. `./mvnw -B verify` green.
2. Paste the real 100k performance numbers and machine specs.
3. On the 20k dev DB, insert a v1.4 DRAFT (cutoff 700) with SQL as parallax\_owner, POST a replay as aditi, poll to DONE, paste the report summary (approval rates, flips, expected loss, unknown, immature, timings).
4. Tick 13. Commit `PX-13: Strategy Lab replay, honest impact report, golden set, measured performance`. REPORT.

## Do not

Build promotion, maker-checker, shadow or drift (Prompts 14–15).
