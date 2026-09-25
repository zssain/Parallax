# Prompt 04 of 23 — The decision engine

## Context

Parallax's engine turns an `EngineInput` and a `RuleConfig` into a `Decision`. It is the heart of the project: live decisions, the reproduce endpoint (which proves an old decision is byte-for-byte repeatable) and Strategy Lab replays over tens of thousands of stored inputs all call it. Prompt 03 built its types, configs and validator. This prompt builds `DecisionEngine` and proves it with worked examples and property tests.

## Session rules

1. `git log --oneline -3` and `./mvnw -q -B verify` first. Red → stop.
2. Read `AGENTS.md`, `docs/SPEC.md` §4, `docs/DECISIONS.md`, and the Prompt 03 types (use them; do not redefine).
3. The engine must not use static mutable state, randomness, time, I/O or logging.
4. If a worked example fails, the engine is wrong, not the example. Re-read SPEC §4 and fix the code. Never edit expected values.

## Build — `com.parallax.engine.scoring.DecisionEngine`

`public final class DecisionEngine { public static Decision evaluate(EngineInput in, RuleConfig c) }` — private constructor. Steps in this exact order:

1. **Fraud flags** (list, in this order): F01 if `addressMismatch`; F02 if `ssnIssuanceYear < birthYear`; F03 if `deceased`; F04 if `velocity24h >= 3`.
2. **atpMax**: `double residual = annualIncome / 12.0 - monthlyHousing - monthlyDebt - livingCost; int atpMax = Math.max(0, (int) Math.floor(residual * atpShare / minPayPct / 100.0) * 100);`
3. **Policy checks**, always all four, in order P02, P03, P04, P01, with names and details exactly as SPEC §4 (P01 detail uses `Usd.format(atpMax)`).
4. **Score parts**, in attribute order, with the band edges, band labels, value strings and codes of SPEC §4. Value strings: utilization `Math.round(u * 100) + "%"`; inquiries, delinquencies and tradelines as plain integers; file age `n + " mo"`; income `Usd.format(annualIncome)`. `maxPoints` = max of that attribute's points array. `score = 300 + Σ points`.
5. **Outcome**: any fraud flag → REFER; else any failed policy check → DECLINED; else `score >= approveCutoff` → APPROVED; `score >= referCutoff` → REFER; else DECLINED.
6. **Limit**: only if APPROVED: `min(first bandLimit with score >= minScore, atpMax)`; otherwise 0.
7. **Reason codes**: only if outcome ≠ APPROVED: failed policy codes in order P02, P03, P04, P01; then score parts with `pointsLost() > 0`, sorted by pointsLost descending with a **stable** sort (ties keep attribute order); cap the total at 4. Never add F-codes or B01.
8. Return `new Decision(outcome, score, limit, reasons, fraudFlags, policyChecks, scoreParts, atpMax)`.

Also add `com.parallax.engine.scoring.Segments` with `static String scoreBand(int score)` → “<620”, “620–679”, “680–719”, “720–759”, “760+” (used by replay segments later).

## Tests

### Worked examples — `DecisionEngineExamplesTest` (config v1\_3)

Build each input with a small test builder whose defaults are: independentIncome true, bureauConsent true, addressMismatch false, deceased false, velocity24h 1, ssnIssuanceYear = birthYear + 1, pullType HARD, birthYear = 2026 − age. Assert **every** column:

| Case | Inputs | Outcome | Score | atpMax | Limit | Reasons / flags |
| --- | --- | --- | --- | --- | --- | --- |
| Priya | age 31, income 38000, housing 1300, debt 520, util 0.82, inq 5, delq 2, trades 4, file 30 | DECLINED | 445 | 1700 | 0 | \[R22, R31, R14, R05\] |
| Ishaan | age 30, income 64000, housing 1350, debt 280, util 0.08, inq 0, delq 0, trades 12, file 156 | APPROVED | 830 | 29200 | 12000 | \[\] |
| Near-prime | age 30, income 45000, housing 1200, debt 300, util 0.55, inq 3, delq 0, trades 5, file 40 | REFER | 655 | 12200 | 0 | \[R31, R14, R05, R33\] |
| Under-21 | age 20, indep false, income 28000, housing 600, debt 50, util 0.20, inq 1, delq 0, trades 1, file 10 | DECLINED | 685 | 5600 | 0 | \[P03, R05, R07, R33\] |
| ATP fail | age 41, income 33000, housing 1150, debt 500, util 0.82, inq 5, delq 2, trades 4, file 30 | DECLINED | 445 | 0 | 0 | \[P01, R22, R31, R14\] |
| Velocity | Ishaan with velocity24h 3 | REFER | 830 | 29200 | 0 | reasons \[R07, R33\], fraudFlags \[F04\] |

Also assert for Priya: the P01 detail is “Max affordable limit $1,700”; score parts values and bands are “82%”/“75%+”, “5”/“5+”, “2”/“2+”, “4”/“2–4”, “30 mo”/“2–4 yr”, “$38,000”/“$25–50k”.

### Other example tests — `DecisionEngineRulesTest`

- F02: ssnIssuanceYear = birthYear − 3 → fraudFlags contains F02, outcome REFER.
- F03 deceased → F03. F01 addressMismatch → F01. All four at once → \[F01, F02, F03, F04\] in that order.
- Fraud beats policy: under-21 without income plus F01 → REFER (not DECLINED), reasons still start with P03.
- P04: consent false → DECLINED, reasons contain P04. P02: age 17 → first reason P02.
- Band edges: utilization 0.0999 vs 0.10, 0.2999 vs 0.30, 0.4999 vs 0.50, 0.7499 vs 0.75; income 24999 vs 25000, 49999/50000, 99999/100000; file age 23/24, 59/60, 119/120; tradelines 1/2, 4/5, 10/11 — each lands in the band SPEC says.
- Limit capped by ATP: a 830-score applicant with atpMax 3000 gets 3000.
- v1\_2 vs v1\_3 on the near-prime input: v1\_3 score 655, v1\_2 score 660 (utilPts\[3\] 45 vs 50); both REFER.
- Reasons cap: a case with 2 failed policies and 5 losing attributes has exactly 4 reasons, policies first.

### Property tests — `DecisionEnginePropertiesTest` (jqwik, 1,000 tries each, v1\_3)

Arbitraries over valid ranges: age 16–80, income 0–300,000, housing and debt 0–5,000, utilization 0.0–1.0 (3 decimals), inquiries 0–10, delinquencies 0–5, tradelines 0–30, file age 0–400, booleans free, velocity 0–5, ssnIssuanceYear birthYear−5..birthYear+20.

1. Determinism: two evaluations are `equals`.
2. Lower utilization (all else equal) never lowers the score.
3. Fewer delinquencies never lowers the score. Fewer inquiries never lowers the score.
4. `creditLimit <= atpMax` always; `creditLimit > 0` iff APPROVED.
5. `reasonCodes.size() <= 4`; empty iff APPROVED.
6. reasonCodes never contain B01 or an F-code; fraudFlags only contain F-codes.
7. 300 ≤ score ≤ 850.
8. Any fraud flag ⇒ REFER.
9. Age never changes the score: same input with a different age (both ≥ 21) gives the same score.

## Definition of Done (real output)

1. `./mvnw -B -pl parallax-engine verify` → green; paste Surefire totals and the jqwik summary lines (tries per property).
2. `PurityArchTest` still passes.
3. Full `./mvnw -q -B verify` green.
4. Tick 04 in AGENTS.md. Commit `PX-4: pure decision engine with worked examples and property tests`.
5. REPORT.

## Do not

Add Spring, JSON libraries, persistence, logging, or anything not listed. Do not add CLI policy yet (Prompt 18).
