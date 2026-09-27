---
name: add-policy-rule
description: >-
  Use when adding a new policy check (a pass/fail eligibility gate that can DECLINE an applicant, like
  legal capacity or ability-to-pay) to the Parallax decision engine. Covers the reason code, the
  PolicyCheck order in DecisionEngine, a worked-example test and the adverse-action-notice test, and the
  SPEC update. Not for scored features with bands and points — use add-scorecard-attribute for those.
---

# Add a policy rule

A policy check is a deterministic eligibility gate (SPEC §4): it is always computed, and any failure with
no fraud flag makes the outcome DECLINED. The score is still computed regardless. Policy failures produce
applicant-facing reason codes and can drive the adverse action notice. The engine is pure — no Spring, no
I/O, no clock, no randomness.

Read `docs/SPEC.md` §4 (Policy checks, Reason codes, Reason texts) first.

1. **Add the reason code and its applicant-facing text.** In
   `parallax-engine/src/main/java/com/parallax/engine/model/ReasonCode.java` add the new `P0x` code with the
   description copied verbatim from SPEC §4 "Reason texts". Policy codes `P*` are applicant-facing (unlike
   the internal `B01`/`F*` codes).

2. **Model the check.** A policy check is represented by
   `parallax-engine/src/main/java/com/parallax/engine/model/PolicyCheck.java` (code, name, passed, detail).
   Use the SPEC §4 name and the exact `detail` wording for both the pass and fail cases.

3. **Evaluate it in order.** In `parallax-engine/src/main/java/com/parallax/engine/scoring/DecisionEngine.java`
   add the check to the policy stage in the SPEC §4 order (currently P02, P03, P04, P01 for reason-code
   ordering; the checks are computed in the documented order). A failed policy with no fraud flag sets the
   outcome to DECLINED; reason codes list failed policy codes first, in that order, then scorecard codes,
   capped at 4.

4. **Write a worked-example test.** Add a case to
   `parallax-engine/src/test/java/com/parallax/engine/scoring/DecisionEngineRulesTest.java` that fails the
   new check and asserts outcome DECLINED, the reason codes (policy code first) and the `PolicyCheck`
   detail string. If it belongs in the SPEC §4 worked-examples table, also update
   `parallax-engine/src/test/java/com/parallax/engine/scoring/DecisionEngineExamplesTest.java`.

5. **Cover the adverse action notice.** A DECLINED decision whose base has an applicant-facing reason
   drives the notice from the deterministic template `AAN-v2`, never from an LLM. The notice is built by
   `application-service/src/main/java/com/parallax/application/query/AdverseActionNotice.java`; extend
   `application-service/src/test/java/com/parallax/application/decide/DeclineIT.java` with a case that
   declines on the new policy code and asserts the notice text lists its reason.

6. **Update the SPEC.** Add the new check to the Policy checks list and the Reason texts list in
   `docs/SPEC.md` §4, in the correct order.

7. **Verify.** Run `./mvnw verify` from the repo root; the reactor, including
   `parallax-engine/src/test/java/com/parallax/engine/PurityArchTest.java`, must be green.
