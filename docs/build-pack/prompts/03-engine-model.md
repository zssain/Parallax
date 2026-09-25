# Prompt 03 of 23 — Engine model, rule configs and validation

## Context

Parallax decides credit card applications with one pure, deterministic engine in the `parallax-engine` module. Live decisions (decision-service), reproduce checks and Strategy Lab replays (application-service) and the credit-line-increase policy (account-service) all run this exact library, so it must be correct, pure and stable. Prompts 01–02 created the skeleton and `docs/SPEC.md`. This prompt builds the engine's **types, rule configurations and config validator**. Prompt 04 builds the evaluation logic on top of them.

## Session rules

1. `git log --oneline -3` and `./mvnw -q -B verify` first. Red → stop and report.
2. Read `AGENTS.md`, `docs/SPEC.md` §4 (your contract) and `docs/DECISIONS.md`.
3. Build exactly what is listed. Java 21 records and enums. No dependencies except test scope.
4. Gaps → simplest option consistent with SPEC + one line in `docs/DECISIONS.md`.
5. Report only real output.

## Build (module `parallax-engine`)

### 1. Purity guardrails first

- `parallax-engine/pom.xml`: no compile or runtime dependencies. Test scope: `junit-jupiter`, `assertj-core`, `jqwik`, `archunit-junit5`.
- `maven-enforcer-plugin` execution `ban-frameworks` with `bannedDependencies` excluding `org.springframework*`, `jakarta.*`, `com.fasterxml.jackson*` in compile and runtime scope.
- `com.parallax.engine.PurityArchTest` (ArchUnit): no class in `com.parallax.engine..` depends on `java.io..`, `java.nio.file..`, `java.net..`, `java.sql..`, `java.time.Clock`, `java.time.Instant`, `java.time.LocalDate`, `java.util.Random`, `java.util.concurrent.ThreadLocalRandom`, `java.security.SecureRandom` or `org.springframework..`. Also: no public static non-final fields.

### 2. Model — package `com.parallax.engine.model` (exact names)

- `enum Outcome { APPROVED, REFER, DECLINED }`
- `enum PullType { HARD, SOFT }`
- `enum ReasonCode` with constants `R31, R14, R22, R07, R05, R33, P01, P02, P03, P04, B01, F01, F02, F03, F04`, each constructed with its description (text exactly from SPEC §4 “Reason texts”, including “(internal)” suffixes) and methods `String description()`, `boolean applicantFacing()` (false for B01 and F01–F04), `boolean isFraud()` (F01–F04).
- `record EngineInput(int age, int birthYear, int annualIncome, int monthlyHousing, int monthlyDebt, double revolvingUtilization, int inquiries6m, int delinquencies24m, int openTradelines, int fileAgeMonths, boolean independentIncome, boolean bureauConsent, boolean addressMismatch, int ssnIssuanceYear, boolean deceased, int velocity24h, PullType pullType)`. Compact constructor throws `IllegalArgumentException("<field> must be ...")` unless: age 0–130; birthYear 1900–2100; annualIncome, monthlyHousing, monthlyDebt ≥ 0; revolvingUtilization 0.0–1.0 inclusive and not NaN; inquiries6m, delinquencies24m, openTradelines, fileAgeMonths, velocity24h ≥ 0; ssnIssuanceYear 1900–2100; pullType non-null.
- `record BandLimit(int minScore, int limit)`
- `record RuleConfig(int approveCutoff, int referCutoff, double minPayPct, double atpShare, int livingCost, int minLimit, List<BandLimit> bandLimits, List<Integer> utilPts, List<Integer> inqPts, List<Integer> delqPts, List<Integer> tradelinePts, List<Integer> fileAgePts, List<Integer> incomePts, double ccf, double lgd)`. Compact constructor copies every list with `List.copyOf` (null list → NullPointerException). It does **not** validate rules; that is the validator's job.
- `record PolicyCheck(ReasonCode code, String name, boolean passed, String detail)`
- `record ScorePart(String attribute, String value, String band, int points, int maxPoints, ReasonCode code)` with `int pointsLost()` = maxPoints − points.
- `record Decision(Outcome outcome, int score, int creditLimit, List<ReasonCode> reasonCodes, List<ReasonCode> fraudFlags, List<PolicyCheck> policyChecks, List<ScorePart> scoreParts, int atpMax)`; lists copied with `List.copyOf`.
- `final class EngineVersion { public static final String VALUE = "engine-1.0.0"; }` and `final class ScorecardVersion { public static final String VALUE = "sc-2.1"; }` (private constructors).

### 3. Formatting helper — `com.parallax.engine.model.Usd`

`static String format(int dollars)` → “$29,200”, “$0”, “−$100” for negatives. Implement grouping by hand (no Locale), so output never depends on the machine.

### 4. Configs — package `com.parallax.engine.config`

- `final class RuleConfigs` with `static RuleConfig v1_2()` and `static RuleConfig v1_3()` returning the exact values from SPEC §4.
- `final class RuleConfigValidator` with `static List<String> validate(RuleConfig c)` returning one human-readable message per violated rule in SPEC §4 “Config validation” (empty list = valid). Every message names the field and the offending value. Required wording for the cutoff-order rule: `Cutoffs out of order: referCutoff (700) must be below approveCutoff (680)`. Array-length checks come first; if a length is wrong, skip the ordering checks for that array (avoid index errors). Sum of maxima: `Sum of attribute maxima must be 550 (got 560)`.

## Tests (JUnit 5 + AssertJ)

- `RuleConfigsTest`: every field of v1\_3() and v1\_2() equals SPEC §4 (assert each explicitly).
- `RuleConfigValidatorTest`: v1\_2() and v1\_3() return empty lists; a `@ParameterizedTest` with one case per rule (at least 18 cases: each cutoff out of range, order, atpShare, minPayPct, livingCost, minLimit, bandLimits not descending by score, not descending by limit, last minScore ≠ 0, top limit > 25,000, each wrong array length, utilPts increasing, inqPts increasing, delqPts increasing, fileAgePts decreasing, incomePts decreasing, a negative point, sum ≠ 550, ccf > 1, lgd < 0), each asserting exactly one message and that it contains the field name. Also one test proving tradelinePts `[20,55,70,60]` is accepted.
- `EngineInputTest`: utilization 1.2 and −0.1 rejected; negative income rejected; the message names the field; a valid input constructs.
- `ReasonCodeTest`: descriptions match SPEC text exactly for all 15; applicantFacing false exactly for B01, F01–F04.
- `UsdTest`: 0, 999, 1000, 29200, 1234567, −100.
- `PurityArchTest` passes.

## Definition of Done (real output)

1. `./mvnw -B -pl parallax-engine verify` → BUILD SUCCESS with the Surefire totals line.
2. Prove the enforcer works: temporarily add `spring-core` to parallax-engine, show the enforcer failure line, remove it, show green again.
3. Full `./mvnw -q -B verify` green.
4. Tick 03 in AGENTS.md. Commit `PX-3: engine model, rule configs, validator, purity rules`.
5. REPORT.

## Do not

Implement `DecisionEngine` (Prompt 04), add Jackson annotations, or add any runtime dependency.
