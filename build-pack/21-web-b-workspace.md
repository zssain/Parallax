# Prompt 21 of 23 — Web app B: the Workspace screens

## Context

Prompt 20 built the web foundation: prototype CSS, icons, the generated API types and hooks, auth, toasts, modals, the marketing site, login and the shell. This prompt builds the five **Workspace** screens — Overview, New application, Decisions, Decision detail, Review queue — by porting each prototype view function (`VIEWS.overview`, `VIEWS.apply`, `VIEWS.decisions`, `VIEWS.decision`, `VIEWS.queue`) to React. Keep the markup, class names, copy and interactions; replace every read of the prototype's in-memory state (`LEDGER`, `APPS`, `S`, `HIST`) with the hook for the §15 endpoint listed below. Where the prototype faked something (pipeline timings, idempotency outcomes), show the real server result.

## Session rules

1. `git log --oneline -3`, `./mvnw -q -B verify`, `cd web && npm run build`. Red → stop.
2. Start the seeded stack (Postgres, bureau-mock dev, decision-service, application-service dev, assistant-service, account-service) and `npm run dev`.
3. Read `docs/design/UI-INVENTORY.md` parts 4–5 for these screens and the matching functions in `docs/design/prototype.html`. Copy text verbatim from the prototype.
4. Use only generated types and existing hooks; if a screen needs data no endpoint returns, stop and tell me instead of inventing a field.
5. Build each screen, then check it in the browser against the prototype before moving on.

## Build

### 1. Overview (`/app`) — port `VIEWS.overview`; data `GET /api/v1/overview`

- Header: eyebrow “Overview”, title “Portfolio at a glance”, description verbatim, `LiveChips`.
- KPIs: Decisions (`decisions`, “applications in ledger”), Approval rate (`approvalRate` as %, 1 dp; “{approved} approved”), Review queue (`reviewQueue`, “open REFERs”, warn class when > 0), Live rules (`liveVersion.version`, “since {since}”, accent), Score drift (PSI) (`psi.value` to 3 dp, `psi.status` lowercase, class ok/warn/bad; “—” when null).
- “Needs attention”: `attention[]` rows with the dot class from `severity`; click → `queue` → `/app/queue`, `lab:v1.4` → `/app/lab?v=v1.4`, `system` → `/app/system`, `drift` → `/app/drift`.
- “Outcome mix”: stacked bar and legend from approved / refer / declined. Trend: label “Approval rate · last 12 months” (the prototype's sample-size text is dropped because the API does not return it — list this in the report), the SVG polyline and dots from `approvalTrend` (skip null months), x-axis labels = first month as “Oct 2025” and last as “Sep 2026 · 83.2%”.
- “Recent decisions” with “View all →”, rendered by `DecisionTable` from `recent`.

### 2. New application (`/app/apply`) — port `VIEWS.apply`, `updPreview`, `validateForm`, `submitApp`; data `POST /api/v1/applications`, `GET /api/v1/lab/versions/live`

- Form sections and fields exactly as the prototype (Applicant; Financials; Product & demo controls; consent checkbox), defaults Meera Joshi, 1996-04-18, 48 Elm Street, Columbus OH, 64000 / 1350 / 280, independent income Yes, Rewards Card, profile Near-prime, scenario None. Products map to `REWARDS_CARD`, `STORE_CARD`, `HEALTHCARE_CARD`.
- **SSN from the demo controls (SPEC §16):** first digit 9; second digit from the profile (Prime 1, Near-prime 3, Subprime 6, Thin file 8); third digit from the scenario (None 1, Address mismatch 7, SSN before DOB 8, Deceased 9, Velocity 1); six random digits. Regenerate when either select changes; the field stays editable. For **Velocity**, keep one SSN until three submissions have been made and show “submission n of 3” under the scenario select. The selects are never sent to the API.
- Right column: “Request” card (`POST /api/v1/applications`, Idempotency-Key with “new key”, JSON preview of the exact body with the SSN shown as `***-**-1234`) and “Live pre-check” (age, residual monthly income, max affordable limit, debt-to-income) computed with the LIVE config from `GET /api/v1/lab/versions/live` using the SPEC §4 formula; note “Client-side estimate only. The engine is the source of truth.”
- Client validation mirrors `validateForm`; server `fieldErrors` map to the same fields (`.f.err`, hint text replaced). 400 → toast “400 Bad Request — fix the highlighted fields” (bad).
- **Submit:** open the locked pipeline modal (“Deciding application”, “Synchronous orchestration by application-service”) with the six step rows in the running state; when the response arrives, reveal each `pipeline[]` item in order \~150 ms apart with its real status icon (✓ / ! / skipped), `ms` and `detail`. Then the result row: outcome pill, “score n” or “bureau unavailable — will auto re-decide”, and “Open decision →”. 202 ENGINE\_PENDING shows “queued for retry” and still links to the decision.
- **Same key again:** 200 with `Idempotent-Replay` → toast “\<b>200 · Idempotent replay.\</b> Same key and body — returned the stored response for APP-x. No duplicate created.” (ok) and open the decision; 422 → “\<b>422 Unprocessable.\</b> This Idempotency-Key was already used with a different request body.” (bad).
- **Simulate double-click:** send two requests with the same key concurrently. Drive the modal from the first; for the second show the real result: 409 → “\<b>409 Conflict\</b> for the second click — the first request with this key is still IN\_PROGRESS.” (warn); 200 replay → the idempotent-replay toast.
- 403 → `denyToast('UNDERWRITER')`. Reset restores defaults.
- After success, invalidate decisions, overview, queue, ledger, system queries.

### 3. Decisions (`/app/decisions`) — port `VIEWS.decisions`; data `GET /api/v1/applications`

Filter buttons “All · n”, “Approved · n”, “Refer · n”, “Declined · n” from `counts`; search box “Search name or APP-ID” sends `q` (debounced 250 ms; the API searches ids only — change the placeholder to “Search APP-ID” and list this difference); `DecisionTable` (size 100, newest first); AUDITOR note “Names are masked for the AUDITOR role.” Row click → detail.

### 4. Decision detail (`/app/decisions/:id`) — port `VIEWS.decision`, `gaugeSVG`, `ledgerCard`, `reproduce`, `showNotice`; data `GET /api/v1/applications/{id}`, `GET /api/v1/decisions/{seq}/reproduce`, `GET /api/v1/applications/{id}/adverse-action-notice`

- Header actions: “← Back”; “Adverse action notice” when current or base outcome is DECLINED; “Review in queue” when current outcome is REFER (→ `/app/queue?app=ID`); “✦ Ask assistant” (→ `/app/assistant?ask=` the prototype's question text, which the Assistant screen sends automatically).
- Banner from `current` (outcome class, id · product, name, “decided under {base.ruleVersion} · ledger seq #{base.seq}”, the override and re-decided suffixes, limit or score on the right).
- `bureau` null and base reason B01 → the “Bureau unavailable” card with the prototype text + ledger trail only. `status` ENGINE\_PENDING → a card “Waiting for the decision engine” and poll every 3 s until decided.
- Otherwise: Score card (gauge with the approve-cutoff tick from `breakdown.approveCutoff`, note “300–850 · approve ≥ X (gold tick) · refer ≥ Y”, and for APPROVED the three limit rows from `breakdown.bandLimit`, `base.atpMax`, `base.creditLimit`); Reason codes (applicant-facing, `.rc`), internal fraud flags section (`.rc.f`) and the ranking note; Policy & identity checks (`breakdown.policyChecks` PASS/FAIL + four fraud rows F01–F04 FLAG/CLEAR from `base.fraudFlags`); Scorecard breakdown table (Attribute, Value, Band, Points, Lost, bar) with the “300 base + … = score. Age is not a scoring factor.” note; Engine input snapshot (`base.engineInput` JSON) with “Reproduce ↻” → result row + toast “Reproduced: identical result ✓” (ok) or “Reproduce mismatch ✗” (bad), text “GET /decisions/{seq}/reproduce → re-ran stored input under {version}: identical outcome, score, limit and reason codes.”; Bureau pull card (pull type pill, Pull ID, Report reuse, SSN at rest = `ssnEncPreview`, “SOAP response → mapped to JSON” + `rawXml`); Ledger trail (`trail` with `prev → hash`, override notes) and the shadow block when `shadow` is present.
- Adverse action notice modal: “Adverse action notice”, “Rendered from a deterministic template with the ledger's reason codes. No AI involved.”, then the API text in `.notice` (first line as the `h4`, numbered lines as an `<ol>`, the footer line in mono), Close. 404 → toast with the API detail.

### 5. Review queue (`/app/queue`) — port `VIEWS.queue`, `submitReview`; data `GET /api/v1/reviews/queue`, `POST /api/v1/reviews/{id}`, `GET /api/v1/reviews/override-stats`, and `GET /api/v1/applications/{id}` for the selected item

- Header chip “n open”. Left: Queue table (id + name, score, pill `fraud` / `bureau` / `score` / `engine`), selection highlight `.qsel`, preselect `?app=` or the oldest. “Override rate by band” table with all four bands from the API and the prototype's note.
- Right panel: title with REFER pill, Score, “Why it was referred” pills with titles, fraud rows, the B01 note for bureau items; form (Decision, Credit limit shown only for Approve, default `suggestedLimit`, hint “Capped at ability-to-pay max $x”; override code O1–O5 with descriptions; note textarea with placeholder “What did you verify and how?”); “Open full decision”; “Record decision”; note “Recorded as a new OVERRIDE ledger entry linked to #{baseSeq}. The original decision is never modified.”
- Submit: 201 → toast “{id} {approved|declined} by {name}. Override written to the ledger.” (ok), select the next item, invalidate queue, decisions, overview, ledger, stats. 422 → toast with the API detail (bad). 403 → `denyToast('UNDERWRITER')`. Empty queue → “The review queue is empty. 🎉”.

## Checks

- `npm run build` and `npm run lint` pass; no `any` in new code except generated files.
- Walk through as Priya (underwriter): submit Meera (REFER), open it, reproduce, review it from the queue (approve 1500, O2); as Sam (auditor): masked names in Decisions and a denied submit; APP-1041's notice lists R22, R31, R14, R05 descriptions.
- Screenshot each of the five screens with Playwright next to the prototype's equivalent into `docs/screenshots/compare/` and list remaining visible differences.

## Definition of Done (real output)

1. Build and lint output.
2. The walkthrough results (what you saw, including toast texts) and the difference list.
3. Tick 21. Commit `PX-21: workspace screens wired to the API`. REPORT, including every intentional difference from the prototype.

## Do not

Build Strategy, Governance or Lifecycle screens (Prompt 22) or change backend contracts.
