# Prompt 14 of 23 — Rule governance: lifecycle, maker-checker, promote, rollback

## Context

The Strategy Lab UI shows version cards (status chip, note, who created and approved it, “Immutable · used by N decisions”), a workflow strip (Draft → Replayed → Proposed → Live), a parameter table comparing live vs candidate with changed rows highlighted and inline validation errors, and action buttons that depend on status: Run replay / Discard (DRAFT); Run in shadow mode / Edit (back to draft) / Propose for approval (REPLAYED); Reject / Approve & promote to LIVE (PROPOSED); Roll back to vX (LIVE). The person who proposes can never approve (maker-checker), and every promotion and rollback is a GOVERNANCE row in the ledger. This prompt builds those APIs.

## Session rules

1. `git log --oneline -3`, `./mvnw -q -B verify`, `docker compose up -d postgres`. Red → stop.
2. Read `AGENTS.md`, `docs/SPEC.md` §10 and every `/api/v1/lab/versions*` and `/api/v1/lab/rollback` row of §15, and `docs/DECISIONS.md`. Read `LiveRuleService`, `ReplayService`, `LedgerWriter`, `CanonicalJson`.
3. Shapes exactly §15. Real output only.

## Build (application-service, package `com.parallax.application.lab`)

### 1. `RuleVersionService` + `LabVersionController`

- `GET /api/v1/lab/versions` (INTERNAL, ASSISTANT): all versions newest first with `usedBy` = count of ledger rows with that rule\_version and kind DECISION, REDECISION or OVERRIDE; `latestReplayJobId`; `config`; `shadow` (false until Prompt 15). Plus `liveVersion` and `rollbackTarget` = the RETIRED version with the latest `retired_at` that was once LIVE (`promoted_at` not null or it is v1.2), or null.
- `GET /api/v1/lab/versions/live` (INTERNAL, CLIENT): `{version, since: promoted_at date, config}`.
- `POST /api/v1/lab/versions` (STRATEGIST) `{config?, note?}`: 409 “Finish or discard the open candidate first — one candidate at a time.” if any version is DRAFT, REPLAYED or PROPOSED; version = “v1.” + (max minor + 1); config defaults to a copy of LIVE; validate (422 `{errors}`); note default “Candidate drafted from \<live>.”; created\_by = display name; config\_hash via `CanonicalJson`. 201 with the version.
- `PUT /api/v1/lab/versions/{v}/config` (STRATEGIST): 409 if first\_used\_at is set (“Version vX is immutable: used by N decisions”); allowed for DRAFT, or REPLAYED (which returns it to DRAFT); 409 for other statuses; validate → 422 `{errors}`; recompute config\_hash.
- `POST /api/v1/lab/versions/{v}/draft` (STRATEGIST): REPLAYED → DRAFT.
- `DELETE /api/v1/lab/versions/{v}` (STRATEGIST): only DRAFT or REPLAYED never used; deletes its replay\_flip and replay\_job rows and the rule\_version row. Never a ledger row. 204.
- `POST /api/v1/lab/versions/{v}/propose` (STRATEGIST): requires REPLAYED and a DONE replay\_job whose candidate\_config\_hash equals the current config\_hash (409 “Replay the current configuration before proposing”); sets PROPOSED and proposed\_by = display name.
- `POST /api/v1/lab/versions/{v}/approve` (APPROVER): requires PROPOSED; if the approver's display name equals proposed\_by → **403** “Maker-checker: the proposer cannot approve”. ONE transaction: current LIVE → RETIRED with retired\_at; this version → LIVE with promoted\_at and approved\_by; append a GOVERNANCE ledger row (rule\_version = the new LIVE; governance\_detail `{action:"PROMOTE", version, replaced, proposedBy, approvedBy, replayJobId, note:"v1.4 promoted to LIVE · proposed by Aditi Rao · approved by Vikram Nair · replaced v1.3"}`). After commit: `LiveRuleService.evict()`.
- `POST /api/v1/lab/versions/{v}/reject` (APPROVER) `{note}`: PROPOSED → DRAFT, rejection\_note stored, note field set to “Rejected by \<name> — revise and replay.”.
- `POST /api/v1/lab/rollback` (APPROVER): rollbackTarget must exist; ONE transaction: LIVE → RETIRED (retired\_at), target → LIVE (promoted\_at now); GOVERNANCE row `{action:"ROLLBACK", from, to, by, note:"Rollback: v1.4 → v1.3 by Vikram Nair"}`; evict the cache.
- `GET /api/v1/lab/versions/compare?a=&b=` (INTERNAL, ASSISTANT): every differing config field, lists flattened as `utilPts[3]`, `bandLimits[0].limit`.
- Also: the seeded v1.3 promotion must appear in the ledger. Add a Flyway Java migration `V5__genesis_governance` that, if the ledger has no GOVERNANCE row, appends one for v1.3 dated 2026-08-14 16:02 UTC with note “v1.3 promoted to LIVE · proposed by Aditi Rao · approved by Vikram Nair”. **Order matters:** it must run before any decision. If seeds already exist in your dev DB, reset the volume and reseed; document this in DECISIONS.md.

### 2. Security

Add the §15 roles for every endpoint above.

## Tests

- `LifecycleIT` (seed 2,000): aditi creates v1.4 (cutoff 700) → PUT config → replay to DONE → REPLAYED → propose → vikram approves → v1.4 LIVE, v1.3 RETIRED; GOVERNANCE row with the exact note; a new application is decided under v1.4; rollback → v1.3 LIVE again with a ROLLBACK row; verify ok.
- `MakerCheckerIT`: test user strat2@parallax.dev (STRATEGIST + APPROVER) proposes then approves → 403 with the exact message; vikram can approve.
- `EditRulesIT`: editing a REPLAYED version → DRAFT and propose → 409 until replayed again; editing v1.3 (used) → 409 immutable; invalid config → 422 listing “Cutoffs out of order…”; second candidate while one is open → 409.
- `CompareIT`: v1.2 vs v1.3 → exactly approveCutoff and utilPts\[1\], utilPts\[2\], utilPts\[3\], utilPts\[4\] differences.
- `RollbackTargetIT`: initially rollbackTarget = v1.2.

## Definition of Done (real output)

1. `./mvnw -B verify` green.
2. On the dev DB (reseeded after V5 if needed): with curl, create v1.4 (cutoff 700), replay, propose as aditi, approve as vikram; paste the GOVERNANCE ledger row and `GET /api/v1/lab/versions`; then roll back and paste the ROLLBACK row.
3. Tick 14. Commit `PX-14: rule version lifecycle, maker-checker, promote and rollback`. REPORT.

## Do not

Build shadow mode, drift or the overview (Prompt 15).
