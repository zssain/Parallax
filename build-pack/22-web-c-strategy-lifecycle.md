# Prompt 22 of 23 — Web app C: Strategy, Governance and Lifecycle screens

## Context

This prompt finishes the UI: **Strategy Lab** (the headline feature), **Drift monitor**, **Decision ledger**, **Assistant**, **System**, plus the two Lifecycle screens, **Accounts** and **Collections**. The first five port prototype view functions exactly (`VIEWS.lab` with `reportHTML`, `compareFlip`, `shadowHTML`; `VIEWS.drift`; `VIEWS.ledger` with `verifyUI`, `tryUpdate`, `tamperTest`; `VIEWS.assistant` with `ask`; `VIEWS.system` with `setBureau`). Accounts and Collections are new (SPEC §16) and must use the same card language: `PageHeader`, `.kpis`, `.card`, tables, `.st` chips, `.mbar` bars, `.btn` styles.

## Session rules

1. `git log --oneline -3`, `./mvnw -q -B verify`, `cd web && npm run build`. Red → stop.
2. Start the seeded stack and `npm run dev`.
3. Read `docs/design/UI-INVENTORY.md` parts 4–5 for these screens and the matching prototype functions. Copy text verbatim.
4. Only generated types and existing hooks. If data is missing, stop and ask; do not invent fields.
5. Check each screen in the browser against the prototype before moving on.

## Build

### 1. Strategy Lab (`/app/lab`, `?v=` selects a version) — data: every `/api/v1/lab/*` endpoint

- Header: “Strategy Lab”, “Test rules on history before they ship”, description verbatim; right: “+ New candidate from {live}”.
- **New candidate** (STRATEGIST): POST `{config: LIVE config with approveCutoff 700}` → select it and toast “{v} created as a DRAFT with approve cutoff 680 → 700. Edit any parameter, then run the replay.”; 409 → warn toast with the API message and select the open candidate.
- **Version cards** (newest first): status chip, shadow pill, version, note, “by {createdBy}”, approver/proposer, “\<b>Immutable\</b> · used by N decisions” or the created date.
- **Selected version card**: title + status actions exactly as the prototype: DRAFT → “Run replay on N decisions” (N = the replay total once known, else “on history”) and “Discard”; REPLAYED → “Run in shadow mode”/“Stop shadow mode”, “Edit (back to draft)”, “Propose for approval”; PROPOSED → “Reject”, “Approve & promote to LIVE”; LIVE with `rollbackTarget` → “Roll back to {target}”. Workflow strip Draft → Replayed → Proposed → Live, or the retired note.
- **Parameter table** (the seven SPEC §10 fields with the prototype's labels and units; atpShare and minPayPct shown as %): Live vs this version, changed rows `.diff`; inputs only when DRAFT and unused. Keep an edited local config; on change PUT it; 422 → show `errors` under “Config validation” as “✗ …” lines and keep the local value; success → “✓ Bands contiguous, cutoffs ordered, ranges valid”. Run replay is disabled while errors exist. Governance list: Proposer, Approver, and the maker-checker sentence.
- **Replay**: POST → poll `GET /lab/replays/{job}` every 500 ms; progress bar and “{progress} / {total} decisions”; on DONE toast “Replay {jobId} complete: {n} decisions in {totalMs} ms.” (ok) and render the report.
- **Report** (`reportHTML`): header “Replay report · {jobId}” with “{v} vs live {baseline} · {n} historical decisions · load {loadMs} ms · evaluate {evaluateMs} ms · total {totalMs} ms” (load added — list as a difference); four KPIs (Approval rate a → b with ± pts; Decisions flipped with % of history; Expected loss observed or “incl. simulation”, with % change and “limit changes: n”; Outcome unknown with “$x exposure never observed” or “Tightening only — fully observable”; show `immature.count` as a small note under the KPIs: “n recent approvals have no outcome yet and are excluded”); the SIMULATION toggle with its warning pill; flip matrix (rows live, columns candidate, off-diagonal highlighted); the expected-loss note using the report's `assumptions`; segment table with Δ bars; “Flipped applicants” (first 20 from the flips endpoint, observed vs “outcome unknown”) → comparison modal from `flips/{seq}` with two side-by-side decision cards, the stored input and Close.
- **Shadow**: toggle via POST shadow; card “Shadow mode · {v}” with count, disagreements and the table from `shadow-results`; empty text “Waiting for live traffic. Submit an application under New application.”
- Toasts from the prototype for propose (“{v} proposed. It now needs a second person with the APPROVER role.”), approve (“{v} is now LIVE. Promotion recorded in the ledger. New applications use it immediately.”), rollback (“Rolled back to {v}. Recorded in the ledger.” warn). A 403 whose detail is the maker-checker message shows that message (bad); other 403s use `denyToast` with the needed role.

### 2. Drift monitor (`/app/drift`) — `GET /api/v1/drift/latest`, `POST /api/v1/drift/run`, `POST /api/v1/drift/simulate`

Port `VIEWS.drift`: header chip “PSI x · status”; KPIs (PSI total, Baseline `baselineN` “development sample”, Current `currentN` “recent applicants”, Thresholds “0.10 / 0.25”); grouped bars SVG (baseline grey, current accent) with bin labels; “Simulate a market shift” card with the range slider 0–1.5 step 0.05 — on change call simulate and show its result with a pill “SIMULATION — synthetic applicants, not stored” and a “Reset to latest” link; per-bin table (Band, Base, Now, PSI with warn above 0.02); a “Run now” button (STRATEGIST, APPROVER) in the header. 404 latest → empty state “No drift report yet. Run one now.”

### 3. Decision ledger (`/app/ledger`) — `GET /api/v1/ledger`, `/stats`, `/verify`, demo endpoints

Port `VIEWS.ledger`: header buttons “Attempt UPDATE”, “Tamper test”, “Verify chain”; KPIs from stats (Records, Decisions, Overrides, Governance); result card area: verify → “✓ Chain intact — n records verified from genesis” or “✗ Chain broken at seq #n”; attempt-update → a `.code` block showing the statement, the returned error and “-- role parallax\_app has INSERT and SELECT only”; tamper → “Tamper test on a copy: changed seq #m to APPROVED with a $25,000 limit, bypassing the database. Verification: chain breaks at seq #b — the stored hash no longer matches the record's content, and every later link depends on it.” Table newest first, 50 per page with a “Load more” button (difference: the prototype had no paging), rows with applications open the detail, governance rows show the note. Replace the prototype's FNV note with “Hashes are SHA-256 over canonical JSON; inserts are serialized by a Postgres advisory lock.”

### 4. Assistant (`/app/assistant`, `?ask=` auto-sends) — `GET /api/v1/assistant/status`, `/tools`, `POST /api/v1/assistant/chat`

Port `VIEWS.assistant`: header chip “role: ASSISTANT (read-only)”; chat card with the greeting “Hi {first name}. I can explain decisions, summarize replay reports, compare rule versions and report override or drift signals. I only have read access.”; user bubbles, `.tool` chips for each `toolCalls` item (“⚙ {name}({args}) → {summary}”, `.tool.x` when error), assistant bubbles (render text safely: paragraphs, `**bold**`, line breaks; never raw HTML); a typing indicator while waiting; suggestion buttons exactly as the prototype (the replay suggestion uses the newest non-LIVE version that has a replay). Tools panel from `/tools` plus a static last row “approveApplication() · not registered” in red, and the four guardrail lines. `configured: false` or 503 → a card “The assistant model is not configured. Set ANTHROPIC\_API\_KEY for assistant-service and restart it.” with the input disabled. Keep `conversationId` in component state.

### 5. System (`/app/system`) — `GET /api/v1/system/status`, `POST /api/v1/system/bureau-fault`, `/idempotency-keys`, `/bureau-pulls`

Port `VIEWS.system`: Services card (5 rows, dot colours, latency or “timeout”), “From Spring Actuator /health on each service.”; Bureau circuit breaker card with state chip and “Simulate bureau outage” (DOWN) / “Restore bureau & run re-decision job” (NONE) — toasts “Bureau is down. Circuit OPEN — submit an application to see the REFER fallback.” (warn) and “Circuit CLOSED. Re-decision job processed n application(s): ids.” or “Bureau restored. Circuit CLOSED.” (ok); Re-decision queue card; Idempotency keys table (Key, State, Application, Expires) with the prototype note; Bureau pulls table (Pull, Type, Profile, SSN) with its note. Refetch status every 5 s while on this screen.

### 6. Accounts (`/app/accounts`, `/app/accounts/:id`) — account-service endpoints

- List: header eyebrow “Lifecycle”, title “Accounts”, description “Accounts opened from approved decisions through the transactional outbox. Statements, payments and credit line increases.”; KPIs (Accounts, Total balance, Average utilization, Delinquent); table (Account, Applicant, Product, Limit, Balance, Utilization `.mbar` + %, Status `.st`, DPD); empty state “No accounts yet. Approve an application and the outbox opens one within seconds.”
- Detail: header “Account” / `ACC-…` with “← Back”; KPIs (Credit limit, Balance, Utilization, Status · DPD); “Payment history” card: 12 cells for the last 12 statements (ok = paid on time, bad = missed, empty = not yet due) with month labels; Statements table; Transactions table; “Credit line increase” card (requested limit, “Accept a counter-offer” toggle, submit → result as an `.rc` row: outcome, new limit, reasons; toast); “Simulate a month” card (dev: purchases $, payment $, “Paid on time” toggle) with the note “Demo tooling: advances this account's statement clock by one month.”. Writes need UNDERWRITER (`denyToast`).

### 7. Collections (`/app/collections`)

Header “Lifecycle” / “Collections” / “Delinquent accounts by bucket, prioritised for outreach.”; four bucket KPIs (1–29, 30–59, 60–89, 90+ with count and amount due; clicking filters the queue); work queue table (Account, Applicant, DPD, Bucket, Amount due, Balance, Last contact, Priority pill: HIGH bad, MEDIUM warn, LOW muted) with “Offer payment plan” and “Log contact” buttons opening a small modal with a required note → POST action → toast “{type} recorded for {account}.”; empty state “No delinquent accounts. Miss a payment under Accounts → Simulate a month to see one here.”

## Checks

- `npm run build`, `npm run lint` pass.
- Walkthrough: as Aditi create v1.4 (approve cutoff 700), set refer cutoff to 710 and see “Cutoffs out of order”, set it back to 620, replay, read the report, open a flip, start shadow, propose; as Aditi try to approve (denied), as Vikram approve (toast, GOVERNANCE row in the ledger), roll back; verify the chain, attempt UPDATE, tamper test; drift slider to 1.2; System outage → submit an application → restore (re-decided); the assistant (configured or not); open an account, simulate months, a CLI, a missed payment → Collections.
- Playwright screenshots of each screen beside the prototype in `docs/screenshots/compare/`; list remaining differences.

## Definition of Done (real output)

1. Build and lint output.
2. The walkthrough results with the toast texts you saw, and the difference list.
3. Tick 22. Commit `PX-22: strategy, governance and lifecycle screens`. REPORT with every intentional difference.

## Do not

Change backend contracts (if something is missing, stop and ask), add libraries for charts (SVG like the prototype), or use browser storage.
