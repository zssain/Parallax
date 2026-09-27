---
name: add-scorecard-attribute
description: >-
  Use when adding a new scorecard attribute (a scored feature with bands and points) to the Parallax
  decision engine. Covers the RuleConfig field, the validator rules, the DecisionEngine band + ScorePart,
  a new reason code, the property/example tests, the golden set, and the SPEC/UI updates. Not for policy
  rules (use add-policy-rule) or for tuning existing points (edit RuleConfigs and re-run the tests).
---

# Add a scorecard attribute

A scorecard attribute is a scored feature (SPEC §4): a value, a set of bands mapping to a points array,
and a reason code. Adding one touches the pure engine, its tests, the golden set and the docs. The engine
is pure — no Spring, no I/O, no clock, no randomness — so everything here stays deterministic.

Read `docs/SPEC.md` §4 (Engine rules) first; it is the contract these steps implement.

1. **Add the points array to the config.** In `parallax-engine/src/main/java/com/parallax/engine/model/RuleConfig.java`
   add the new `int[] xxxPts` field (record component) with its accessor, keeping it alongside the other
   `*Pts` arrays. Every attribute's points array lives here.

2. **Give both seeded configs real values.** In `parallax-engine/src/main/java/com/parallax/engine/config/RuleConfigs.java`
   add the new array to v1.2 and v1.3 so the sum of attribute maxima still equals 550 (rebalance the other
   arrays as needed — the validator enforces this). These are the LIVE/RETIRED configs of SPEC §4.

3. **Validate the new array.** In `parallax-engine/src/main/java/com/parallax/engine/config/RuleConfigValidator.java`
   add: the exact array-length rule (each violation names the field and value); the ordering rule if the
   attribute is monotonic (non-increasing like `utilPts`, or non-decreasing like `incomePts` — omit it if
   the bands are deliberately unordered like `tradelinePts`); a no-negative-points check; and update the
   **sum of attribute maxima = 550** check to include the new array's maximum.

4. **Score it in the engine.** In `parallax-engine/src/main/java/com/parallax/engine/scoring/DecisionEngine.java`
   compute the band for the new attribute and add points from the new array, in the SPEC §4 attribute
   order. Emit a `parallax-engine/src/main/java/com/parallax/engine/model/ScorePart.java` with the value
   string, band label, points, maxPoints, pointsLost and the new code, in the same order (the API renders
   `scoreParts` in this order and reason codes tie-break by attribute order).

5. **Add the reason code and its SPEC text.** In `parallax-engine/src/main/java/com/parallax/engine/model/ReasonCode.java`
   add the new `Rxx` code with the applicant-facing description copied verbatim from SPEC §4 "Reason texts".
   Reason codes are emitted only when the outcome is not APPROVED, sorted by pointsLost descending, ties in
   attribute order, capped at 4 — no code change needed for that if the ScorePart order is right.

6. **Extend the tests.** Add band-boundary and points cases to
   `parallax-engine/src/test/java/com/parallax/engine/scoring/DecisionEngineRulesTest.java`, a description
   assertion to `parallax-engine/src/test/java/com/parallax/engine/model/ReasonCodeTest.java`, the new
   length/ordering cases to `parallax-engine/src/test/java/com/parallax/engine/config/RuleConfigValidatorTest.java`,
   and confirm the sum-of-maxima property in
   `parallax-engine/src/test/java/com/parallax/engine/scoring/DecisionEnginePropertiesTest.java` still holds
   (1,000 jqwik tries). If any SPEC §4 worked example changes, update
   `parallax-engine/src/test/java/com/parallax/engine/scoring/DecisionEngineExamplesTest.java` to the new
   expected columns.

7. **Regenerate the golden set.** The golden set is generated and reproduced in-process by
   `application-service/src/test/java/com/parallax/application/decide/GoldenReproduceIT.java` (it seeds
   5,000 records and reproduces every tenth). Because the scorecard changed, re-run it so the reproduced
   outcomes match the new engine; it must stay green.

8. **Update the docs.** Add the attribute row (value shown, bands, code) to SPEC §4 in `docs/SPEC.md`, and
   if strategists should edit its points, add it to the editable-parameters list in SPEC §10 and to the
   parameter table in `docs/design/prototype.html` and `docs/design/UI-INVENTORY.md` (the UI source of
   truth).

9. **Verify.** Run `./mvnw verify` from the repo root; the whole reactor, including
   `parallax-engine/src/test/java/com/parallax/engine/PurityArchTest.java`, must be green.
