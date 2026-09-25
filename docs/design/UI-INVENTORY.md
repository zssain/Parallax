# Parallax UI inventory

A precise inventory of `docs/design/prototype.html` (the approved UI). Everything here is read from that file — CSS, markup and script. Copy in quotes is verbatim. The React app must match this; it replaces the prototype's in-browser simulation with the SPEC §15 endpoints.

# Part 1 — Tokens

## `:root` variables

```
--navy:#0b1830;--navy2:#10223f;--navy3:#162c50;--gold:#c9b17c;--gold2:#e6d3a3;--teal:#2e7a80;--teal2:#3f959b;
--serif:"Iowan Old Style","Palatino Linotype","Book Antiqua",Palatino,Georgia,serif;
--sans:ui-sans-serif,-apple-system,BlinkMacSystemFont,"Segoe UI",Inter,Roboto,Helvetica,Arial,sans-serif;
--mono:ui-monospace,SFMono-Regular,Menlo,Consolas,monospace;
```

## `.app` (light) variables

```
--bg:#e8eff0;--panel:#fff;--panel2:#f5f8f8;--ink:#13202b;--muted:#5b6a75;--line:#dbe4e5;--acc:#2e7a80;--accbg:#e1eeee;--bad:#b3261e;--badbg:#fbe9e6;--warn:#9a5b06;--warnbg:#fcf0da;--ok:#2d7a4d;--okbg:#e2f2e8;--info:#2b5a8a;--infobg:#e4edf8;--code:#0f1c2e;
```

## `.app.dark` variables

```
--bg:#121518;--panel:#1b1f23;--panel2:#22272c;--ink:#e6e9ec;--muted:#99a4ad;--line:#2b3136;--acc:#5fb3b8;--accbg:#1d3336;--bad:#f0806f;--badbg:#3a2320;--warn:#e2ab52;--warnbg:#3a2e19;--ok:#6fc79c;--okbg:#1b3326;--info:#86b2e6;--infobg:#1c2a3b;--code:#0c1116
```

## Font stacks

- **Serif** (`--serif`): `"Iowan Old Style","Palatino Linotype","Book Antiqua",Palatino,Georgia,serif` — hero/marketing headlines, page-header banners, notices.
- **Sans** (`--sans`): `ui-sans-serif,-apple-system,BlinkMacSystemFont,"Segoe UI",Inter,Roboto,Helvetica,Arial,sans-serif` — default body font.
- **Mono** (`--mono`): `ui-monospace,SFMono-Regular,Menlo,Consolas,monospace` — IDs, hashes, code blocks, config values.

Wordmark: `.wordmark` (font-weight 800, letter-spacing −.055em), variants `.wordmark.light` (white) and `.wordmark.dark` (navy). Eyebrow: `.eyeb` (uppercase, gold, with a leading dot).

# Part 2 — Components

- **`.phead`** — page header banner on every app view: gold/teal top gradient bar, eyebrow + `<h1>` + description on the left, `.phead-r` actions on the right. Built by `head(eb,t,d,r)`.
- **`.card`** — the standard white panel (rounded, 1px border, soft shadow); `.card h3` is the card title row. Used across every view.
- **`.kpis` / `.kpi`** — a bordered row of metric cells; `.kpis.k4` is the four-up variant. `.kpi` shows `.lbl`, a big `.v`, and a `<small>` caption. On Overview, Drift, Ledger and the replay report.
- **`.oc`** — outcome pills, colour-coded by class: `.oc.APPROVED` (green), `.oc.REFER` (amber), `.oc.DECLINED` (red). In tables, banners and chat.
- **`.st`** — status chips for rule-version state: `.st.LIVE`, `.st.PROPOSED`, `.st.REPLAYED`, `.st.DRAFT`. On Strategy Lab version cards and the System circuit chip.
- **`.btn`** — buttons; variants `.btn.p` (primary/accent), `.btn.bad` (destructive/red), `.btn.sm` (small), `:disabled`. Plus `.link` (text button). Everywhere.
- **`.banner`** — full-width outcome banner on the decision detail: `.banner.APPROVED` / `.banner.DECLINED` / `.banner.REFER` gradients, serif `<h2>`, `.sub` and a big value.
- **`.gauge`** — semicircular score gauge (SVG) on the decision detail Score card; `.gauge .n` overlays the numeric score.
- **`.rc`** — reason-code rows: default red left border; `.rc.f` (fraud, amber) and `.rc.b` (bureau/internal, info blue). `.rc code` is the code chip. On decision detail, queue and the adverse-action notice.
- **`.chkrow`** — a labelled key/value row with a `.pass`/`.fail` marker; policy & identity checks, pre-check, limit breakdown, shadow rows.
- **`.tl` / `.tli`** — vertical timeline and its items (dotted node, bold heading, muted sub, `.hash` line). The ledger trail on decision detail.
- **`.vcard`** — a Strategy Lab version card in the horizontal `.vcards` scroller; `.vcard.on` is the selected one.
- **`.wf`** — the workflow stepper (DRAFT → REPLAYED → PROPOSED → LIVE); `.wf div.done` and `.wf div.cur`. On Strategy Lab.
- **`.mx`** — the flip matrix table (centered cells); `.mx td.hl` highlights off-diagonal (flipped) cells. On the replay report.
- **`.toggle`** — a sliding on/off switch (`.toggle.on`); toggles simulated outcomes in the replay report.
- **`.chat` / `.msg` / `.tool`** — assistant layout: `.chat` two-column grid, `.msg.u` (user, accent) and `.msg.a` (assistant) bubbles, `.tool` dashed tool-call chip (`.tool.x` = error). On the Assistant view.
- **`.modal`** — centered dialog inside `.modal-wrap` overlay (blurred backdrop). Used for the decide pipeline, adverse-action notice, and flip comparison.
- **`.pstep`** — a pipeline step row in the decide modal: `.ico` badge with states `.run` (spinner), `.ok` (green ✓), `.warn` (amber !), and a mono `<small>` timing.
- **`.toast`** — bottom-center transient notification; `.toast.on` shows it; variants `.toast.bad` / `.toast.warn` / `.toast.ok`. Fired by `toast()` everywhere.
- **`.notice`** — serif document block for the adverse-action notice text.
- **`.side` / `.navi` / `.ucard` / `.theme`** — the left sidebar: `.side` container, `.navsec` section labels, `.navi` nav items (`.navi.on` active, `.badge` count), `.ucard` user card (avatar, name/role, switch-user `<select>`, sign-out), `.theme` light/system/dark toggle. `.collapsed` narrows it to icons.
- **Marketing classes** — `#site`, `.mnav` (sticky top nav), `.hero`/`.hero-l`/`.hero-r`/`.arch-svg`/`.plx` (parallax arch art), `.sl-ctrl`/`.sl-prog` (slider), `.msec`/`.steps3`/`.step3` (how-it-works), `.labband`/`.labcard`/`.labrow` (Strategy Lab band), `.gov` (governance grid), `.light-wrap`/`.quiz`/`.qz-*` (five-question check), `.land`/`.lgrid`/`.lcell`/`.lbig` (regulation cards), `.mfoot` (footer); `#login` with `.lg-l`/`.lg-r`/`.lg-form`/`.fin`/`.roles`/`.btn-teal`/`.lg-frame`.

# Part 3 — Icons

SVG path data from the `I` object, keyed by name (rendered by `ic(k,s)` inside a 24×24 stroke SVG):

- **overview**: `<path d="M3 13h4l3-8 4 14 3-6h4"/>`
- **apply**: `<path d="M14 3H6a1 1 0 0 0-1 1v16a1 1 0 0 0 1 1h12a1 1 0 0 0 1-1V8z"/><path d="M14 3v5h5M12 11v6M9 14h6"/>`
- **decisions**: `<path d="M4 6h16M4 12h16M4 18h10"/>`
- **queue**: `<path d="M12 3l9 4-9 4-9-4z"/><path d="M3 12l9 4 9-4M3 17l9 4 9-4"/>`
- **lab**: `<path d="M9 3h6M10 3v6L4 19a1 1 0 0 0 1 2h14a1 1 0 0 0 1-2l-6-10V3"/><path d="M7 14h10"/>`
- **drift**: `<path d="M3 17l5-5 4 4 8-8"/><path d="M15 8h5v5"/>`
- **ledger**: `<rect x="4" y="3" width="16" height="18" rx="2"/><path d="M8 7h8M8 11h8M8 15h5"/>`
- **assistant**: `<path d="M12 3l1.8 4.6L18 9l-4.2 1.6L12 15l-1.8-4.4L6 9l4.2-1.4z"/><path d="M19 15l.8 2 2 .8-2 .8-.8 2-.8-2-2-.8 2-.8z"/>`
- **system**: `<circle cx="12" cy="12" r="3"/><path d="M12 2v3M12 19v3M2 12h3M19 12h3M4.9 4.9 7 7M17 17l2.1 2.1M4.9 19.1 7 17M17 7l2.1-2.1"/>`
- **collapse**: `<rect x="3" y="4" width="18" height="16" rx="2"/><path d="M9 4v16M15 10l-2 2 2 2"/>`
- **out**: `<path d="M15 4h4v16h-4M10 8l-4 4 4 4M6 12h10"/>`
- **sun**: `<circle cx="12" cy="12" r="4"/><path d="M12 2v2M12 20v2M2 12h2M20 12h2M5 5l1.4 1.4M17.6 17.6 19 19M5 19l1.4-1.4M17.6 6.4 19 5"/>`
- **mon**: `<rect x="3" y="4" width="18" height="12" rx="2"/><path d="M8 20h8M12 16v4"/>`
- **moon**: `<path d="M20 14.5A8 8 0 1 1 9.5 4a6.5 6.5 0 0 0 10.5 10.5z"/>`

# Part 4 — Screens

## Marketing site (sections in order)

1. **Top nav** (`.mnav`): wordmark "parallax.", links "Platform", "Strategy Lab", "Governance", teal button "Request a demo ↗", link "Open workspace ↗".
2. **Hero** (`.hero`, 3 auto-rotating slides with ← → controls and `01/02/03` progress):
   - Slide 1 — eyebrow "Credit decisioning for a moving portfolio"; headline "Change a rule." / em "See every decision it would have made."; lead "Replay your full decision history before a strategy ships."; body "Parallax decides credit card applications, records each decision with its exact inputs, and lets strategists test rule changes against real history — with honest loss estimates." CTAs "Open the workspace ↗", "See how it works ↗".
   - Slide 2 — eyebrow "A ledger you can prove"; "Every decision." / "Reproducible, forever."; lead "Six months later, get the same answer — byte for byte."; body about the append-only, hash-chained ledger.
   - Slide 3 — eyebrow "Champion and challenger"; "Two views." / "One decision."; lead "Run tomorrow's rules silently beside today's."; body about shadow mode, maker-checker and rollback.
   - Hint: "↓   Take the five-question check".
3. **Quick reality check** (`#check`): eyebrow "A quick reality check"; h2 "Five questions." / em "One clearer picture."; intro "For credit strategy and risk teams. If any answer gives you pause, you're not alone — that's exactly the gap Parallax was built for." Five questions (`QZ`), each with three scored options and a "WHERE PARALLAX HELPS" feature:
   - Q1 "Can you reproduce a decision from six months ago — exactly?" → "Decision ledger + reproduce endpoint".
   - Q2 "Before a rule change ships, do you know which past applicants it would flip?" → "Strategy Lab replay".
   - Q3 "Does your loss estimate separate what you observed from what you are guessing?" → "Reject-inference labelling".
   - Q4 "Can the person who proposes a rule change also approve it?" → "Maker-checker promotion + rollback".
   - Q5 "What happens to applications when your credit bureau goes down?" → "Circuit breaker + automatic re-decision".
   - Result labels: "Well governed" (≥9), "Some exposure" (≥5), "Significant exposure" (else); "Retake ↺". Footer "WHAT'S NEXT" / "See each of these working in the workspace."
4. **The rules of the road** (`.land`): eyebrow "The rules of the road"; h2 "Every decision has a deadline."; four regulation cells (each links out): "30 days" — "to notify an applicant of adverse action after a completed application" (ECOA · Regulation B §1002.9); "4 reasons" — "is where Reg B guidance says listing more stops helping the applicant"; "Under 21" — "applicants need an independent ability to pay, or a co-signer" (CARD Act 2009 · Regulation Z §1026.51); "60 days" — "to request a free copy of the credit report used in the decision" (Fair Credit Reporting Act · adverse action).
5. **How Parallax works** (`#how`): eyebrow "How Parallax works"; h2 "Decide. Record. " / em "Replay."; sub "Every application runs through one deterministic engine…". Three steps: "01 — DECIDE" / "A decision in milliseconds"; "02 — RECORD" / "Reproducible, forever"; "03 — REPLAY" / "See before you ship".
6. **Strategy Lab band** (`#lab`): eyebrow "Strategy Lab"; h2 "Two views. " / em "One decision."; sub about champion/challenger and outcome-unknown; button "Open the Strategy Lab ↗"; labcard rows: "Candidate" = "v1.4 · cutoff 680 → 700", "Decisions replayed" = "20,000", "Approval rate" = "−4.1 pts", "Expected loss (observed)" = "−11.8%", "Outcome unknown" = "0 · tightening only".
7. **Governance** (`#gov`): eyebrow "Governance built in"; h2 "Built for how credit teams " / em "actually work."; four items: "Maker-checker", "Adverse action", "Drift monitoring", "A careful assistant".
8. **Footer** (`.mfoot`): "© 2026 Parallax · Portfolio project · All data is synthetic" · "Privacy    Terms".

## Login (`#login`)

Left panel: back button "←  Back to website"; eyebrow "Your Parallax workspace"; "🛡  Secure workspace access"; h1 "Welcome back."; copy "Sign in to continue into your credit decisioning workspace. Demo accounts below let you see each role's view."; label "Demo account" with role buttons (Aditi Rao · strategist "Drafts and replays rule versions"; Vikram Nair · approver "Approves and promotes versions"; Priya Menon · underwriter "Works the review queue"; Sam Iyer · auditor "Reads the ledger, PII masked"); label "Email address" (placeholder "you@company.com"); label "Password" (value "demo-password"); button "Sign in ↗"; footer "© 2026 Parallax" · "Privacy     Terms". Right panel: eyebrow "The credit decisioning platform"; h2 "Two views." / em "One decision."; "Live rules. Candidate rules. / The evidence between them."; arch art; bottom "Decide." "Record." "Replay." Interactions: `pickRole` fills the email; `doLogin` validates a non-empty email and enters the app on Overview.

## App shell

Sidebar (`renderSide`): wordmark "parallax." + collapse button; nav sections **WORKSPACE** (Overview, New application, Decisions, Review queue), **STRATEGY** (Strategy Lab, Drift monitor), **GOVERNANCE** (Decision ledger, Assistant, System). Queue shows an amber count badge; Strategy Lab shows a "1" badge when a version is PROPOSED. User card: avatar, name, role, sign-out; "Switch user: …" select; theme toggle (light/system/dark). Toasts on switch user and sign in.

## Overview (`VIEWS.overview`)

- Eyebrow "Overview"; title "Portfolio at a glance"; description "Live decisions, the rules behind them and what needs attention. Every figure here is read from the decision ledger."; header-right "Live data"/"Bureau outage" dot + "Rules v1.3" chip.
- KPIs: "Decisions", "Approval rate", "Review queue", "Live rules", "Score drift (PSI)".
- Cards: "Needs attention" (clickable `.att` rows built from queue, proposed versions, bureau circuit, re-decision backlog, PSI, shadow); "Outcome mix" (lbl "current decisions") with a stacked bar, legend "approved/refer/declined", and an SVG "Approval rate · last 12 months (20,000 historical decisions)" line from "Oct 2025" to "Sep 2026"; "Recent decisions" (button "View all →") showing the 7 latest via the decisions table.

## Decisions (`VIEWS.decisions`)

- Eyebrow "Decisions"; title "Every application, every outcome"; description "The current state of each application. Click a row to see how the engine decided, reproduce it, or read its ledger trail."; live chips.
- Filter buttons "All", "Approved", "Refer", "Declined" each with a count; search input placeholder "Search name or APP-ID".
- Table columns: "Application", "Applicant", "Product", "Score", "Outcome", "Limit", "Rules", "Recorded" (rows may show "override"/"re-decided" pills). Empty state "No decisions match." Note (AUDITOR only) "Names are masked for the AUDITOR role."

## New application (`VIEWS.apply`)

- Eyebrow "New application"; title "Submit a credit application"; description "Runs the full pipeline: validation, idempotency, SOAP bureau pull, fraud screen, decision engine and a transactional ledger write."; live chips.
- Form (left card) sections: "Applicant" (First name, Last name, "Date of birth" hint "Legal capacity: 18+; under 21 needs independent income", "SSN (synthetic, 9 digits)" hint "Encrypted at rest · masked in logs", "Home address" hint "Compared against the bureau file address"); "Financials (monthly unless noted)" ("Annual income (USD)", "Housing payment", "Other debt payments", "Independent income" hint "Only matters under age 21"); "Product & demo controls" ("Card product": Rewards/Store/Healthcare Card; "Synthetic bureau profile" hint "What the mock SOAP bureau returns"; "Fraud scenario": None / "Address mismatch with bureau file" / "SSN issued before date of birth" / "SSN on deceased list" / "Velocity: 3+ applications in 24 h"; checkbox "Applicant consents to a credit bureau inquiry (hard pull)."). Buttons "Reset", "Simulate double-click" (title "Fires two requests with the same Idempotency-Key"), "Submit for decision ↗".
- Right column: "Request" card — lbl "POST /api/v1/applications", "Idempotency-Key:" with a "new key" link, and a JSON preview; "Live pre-check" card — rows "Age at application", "Residual monthly income", "Max affordable limit", "Debt-to-income"; note "Client-side estimate only. The engine is the source of truth."
- Decide modal: "Deciding application" / "Synchronous orchestration by application-service"; six steps "Validate request", "Idempotency check", "Credit bureau · SOAP pull", "Fraud & identity screen", "Decision engine · v1.3", "Ledger commit · single transaction" with timings/`circuit OPEN`/`skipped`/`n flag(s)`/`seq #n`; result row with "Open decision →".
- Toasts: "400 Bad Request — fix the highlighted fields"; "200 · Idempotent replay…"; "422 Unprocessable…"; "409 Conflict…". Field errors: "Required", "Enter exactly 9 digits", "Enter a valid date", "Must be greater than 0", "Must be 0 or more", "Enter a full address".

## Decision detail (`VIEWS.decision`)

- Eyebrow "Decision detail"; title = the APP id; description "How the engine reached this outcome — policy checks, scorecard points, ranked reason codes — and the ledger evidence that proves it."; actions "← Back", "Adverse action notice" (when declined), "Review in queue" (when open REFER), "✦ Ask assistant".
- Outcome banner (APPROVED/REFER/DECLINED) with score or credit limit.
- Bureau-unavailable variant: card "Bureau unavailable" explaining the B01 fallback + ledger trail.
- Cards: "Score" (gauge, "300–850 · approve ≥ … · refer ≥ …", plus limit breakdown when approved); "Reason codes" (applicant-facing `.rc`, internal fraud flags under "Internal fraud flags · never shown to applicant", note about ranking/cap 4); "Policy & identity checks" (`.chkrow` PASS/FAIL for each policy and the four fraud checks FLAG/CLEAR); "Scorecard breakdown" (lbl "how reasons are ranked"; columns "Attribute", "Value", "Band", "Points", "Lost", and a bar; note "300 base + … points = … . Age is not a scoring factor."); "Engine input snapshot" (button "Reproduce ↻", the stored JSON, reproduce result line); "Bureau pull" (pill = pull type; "Pull ID", "Report reuse", "SSN at rest"; "SOAP response → mapped to JSON"); "Ledger trail" (timeline of records + optional shadow evaluation).
- Adverse-action modal: "Adverse action notice" — deterministic template AAN-v2 ("Notice of action taken · <date>", reason list, ECOA language, footer "Template AAN-v2 · ledger #… · rules …").

## Review queue (`VIEWS.queue`)

- Eyebrow "Review queue"; title "Referred applications"; description "REFERs from the score band, fraud flags or bureau outages. Underwriters approve or decline with a note and an override reason code."; header chip "N open".
- Left: "Queue" card (rows: APP id + masked name, score, kind pill fraud/bureau/score); "Override rate by band" card, columns "Band", "Refers", "Overridden to approve", note "A high override rate in one band suggests it is miscalibrated — a signal for the Strategy Lab."
- Right (selected): review panel with score, "Why it was referred" reason pills, fraud/B01 rows, and a form: "Decision" (Approve/Decline), "Credit limit" (hint "Capped at ability-to-pay max …"), "Override reason code" (O1–O5), "Underwriter note (required)" (placeholder "What did you verify and how?"). Buttons "Open full decision", "Record decision"; note "Recorded as a new OVERRIDE ledger entry linked to #… . The original decision is never modified." Empty state "The review queue is empty. 🎉".
- Validation toasts: "Add a note of at least 10 characters explaining what you verified"; "Limit must be at least $300"; "Limit exceeds the ability-to-pay maximum of …"; success "… approved/declined by … . Override written to the ledger."

## Strategy Lab (`VIEWS.lab`)

- Eyebrow "Strategy Lab"; title "Test rules on history before they ship"; description "Draft a candidate version, replay every historical decision through it, read an honest impact report, then promote through maker-checker approval."; header button "+ New candidate from v1.3".
- Version cards scroller (`.vcards`) with status chips; workflow stepper "Draft → Replayed → Proposed → Live" (retired shows a note "Retired version — kept forever so its decisions stay reproducible.").
- Parameter table: columns "Parameter", "Live <v>", "<candidate>", "Unit"; editable rows: "Approve cutoff", "Refer cutoff", "Ability-to-pay share of residual income", "Estimated minimum payment", "Top-band credit limit (800+)", "Points: utilization 50–74%", "Points: 3–4 inquiries". "Config validation" list ("✓ Bands contiguous, cutoffs ordered, ranges valid" or errors) and "Governance" (Proposer/Approver + maker-checker note).
- Action buttons by status: "Run replay on 20,000 decisions", "Discard"; "Run in shadow mode"/"Stop shadow mode", "Edit (back to draft)", "Propose for approval"; "Reject", "Approve & promote to LIVE"; "Roll back to <v>".
- Replay report card: "Replay report · <jobId>"; KPIs "Approval rate", "Decisions flipped", "Expected loss (observed)"/"Expected loss (incl. simulation)", "Outcome unknown"; toggle "Include simulated outcomes for applicants never observed" + "SIMULATION —" pill; "Flip matrix · rows live, columns candidate"; "Segment impact · by live score band" (columns "Band", "Apps", "Approval live → cand", "Δ", "Loss live → cand"); "Flipped applicants" (columns "Record", "Score live → cand", "Outcome live → cand", "Candidate reasons", "Observed?"). Empty "Run a replay to see approval shifts, flipped applicants, exposure-weighted loss and segment impact."
- Compare modal (`compareFlip`): "<id> · live vs candidate", two engine columns + stored input JSON.
- Toasts on create/propose/approve/reject/rollback/shadow/replay-complete.

## Drift monitor (`VIEWS.drift`)

- Eyebrow "Drift monitor"; title "Population Stability Index"; description "Compares the current applicant score distribution with the development baseline. A scheduled job runs this nightly and alerts above the threshold."; header chip "PSI <n> · <stable|watch|investigate>".
- KPIs: "PSI (total)", "Baseline" ("8,000" · "development sample"), "Current" ("5,000" · "recent applicants"), "Thresholds" ("0.10 / 0.25" · "watch / investigate").
- Cards: "Score distribution" (baseline vs current bars, legend "baseline"/"current"); "Simulate a market shift" (copy about pushing utilization/inquiries up; a 0–1.5 range slider "none … severe"; table columns "Band", "Base", "Now", "PSI").

## Decision ledger (`VIEWS.ledger`)

- Eyebrow "Decision ledger"; title "Append-only, hash-chained"; description "Every decision, override, re-decision and promotion. The service role has no UPDATE or DELETE grant; each record stores the hash of the one before it."; buttons "Attempt UPDATE", "Tamper test", "Verify chain".
- KPIs: "Records", "Decisions", "Overrides", "Governance".
- Table columns: "Seq", "Kind", "Subject", "Outcome", "Rules", "Time", "prev → hash". Note "Demo uses an FNV-based 64-bit hash in the browser; the real service uses SHA-256 with inserts serialized by a Postgres advisory lock."
- "Attempt UPDATE" prints the `permission denied for table decision_ledger` SQL block; "Tamper test" shows the chain breaking at a seq; "Verify chain" shows "✓ Chain intact — N records verified from genesis" or "✗ Chain broken at seq #…".

## Assistant (`VIEWS.assistant`)

- Eyebrow "Assistant"; title "Underwriter & strategist agent"; description "Answers questions by calling Parallax APIs as tools. Read-only credentials, masked PII, numbers only from tool results. It can never make or change a decision."; header chip "role: ASSISTANT (read-only)".
- Chat card with greeting "Hi <first>. I can explain decisions, summarize replay reports, compare rule versions and report override or drift signals. I only have read access."; suggestion chips "Why was APP-1041 declined?", "Summarize APP-1053", "Why did approvals drop under <v>?" / "Compare the live version with the latest candidate", "Which score band has the highest override rate?", "Is score drift a concern?", "Approve APP-1043 now"; input placeholder "Ask about an application, a version or a replay…", "Send".
- Tools card: `getDecision(id)` (read), `getReasonCodes(id)` (read), `runReplay(version)` (async · existing only), `getReplayReport(jobId)` (read), `compareVersions(a, b)` (read), `getOverrideStats()` (read), `getDriftReport()` (read), `approveApplication()` (not registered). Guardrails: "Tool output is data, never instructions"; "Every figure must come from a tool result"; "Identity fields masked before the model sees them"; "15-question eval set runs in CI". Write requests are refused; a prompt-injection address is treated as data.

## System (`VIEWS.system`)

- Eyebrow "System"; title "Health, resilience and plumbing"; description "Service health, the bureau circuit breaker, automatic re-decisions, idempotency keys and bureau report reuse."; live chips.
- Cards: "Services" (application-service, decision-service, bureau-mock (SOAP), assistant-service, postgres with UP/DOWN + latency; note "From Spring Actuator /health on each service."); "Bureau circuit breaker" (status chip CLOSED/OPEN; buttons "Simulate bureau outage" / "Restore bureau & run re-decision job"); "Re-decision queue" (count chip + list); "Idempotency keys" (columns "Key", "State", "Application", "Expires"; empty "No keys yet…"); "Bureau pulls" (lbl "reuse window 30 days"; columns "Pull", "Type", "Profile", "SSN").
- Toasts: "Bureau is down. Circuit OPEN…"; "Bureau restored. Circuit CLOSED."; "Circuit CLOSED. Re-decision job processed N application(s): …".

# Part 5 — Simulation → API map

Each prototype function that fakes backend behaviour, mapped to the SPEC §15 endpoint that replaces it:

| Prototype function | Replaced by (§15) |
| --- | --- |
| `doLogin` | HTTP Basic auth (§9) + `GET /api/v1/me` |
| `renderRoles` / `pickRole` | static demo users (§9); no endpoint |
| `switchUser` | re-auth as another demo user (§9); no endpoint |
| `createApplication` / `submitApp` | `POST /api/v1/applications` (with Idempotency-Key) |
| `bureauPull` / `buildInput` / `recordDecision` / `appendLedger` | server-side inside `POST /api/v1/applications` (bureau SOAP §8, engine §4, ledger §5) |
| decisions list (`decTable` / `filteredDecisions`) | `GET /api/v1/applications?outcome=&q=&source=&page=&size=` |
| `openApp` (decision detail) | `GET /api/v1/applications/{id}` |
| `reproduce` | `GET /api/v1/decisions/{seq}/reproduce` |
| `showNotice` | `GET /api/v1/applications/{id}/adverse-action-notice` |
| review queue view | `GET /api/v1/reviews/queue` |
| `submitReview` | `POST /api/v1/reviews/{id}` |
| "Override rate by band" | `GET /api/v1/reviews/override-stats` |
| overview view | `GET /api/v1/overview` |
| `newCandidate` | `POST /api/v1/lab/versions` |
| `setCfg` | `PUT /api/v1/lab/versions/{v}/config` |
| `discardVersion` | `DELETE /api/v1/lab/versions/{v}` |
| `backToDraft` | `POST /api/v1/lab/versions/{v}/draft` |
| `runReplay` | `POST /api/v1/lab/versions/{v}/replays` + `GET /api/v1/lab/replays/{jobId}` |
| flipped-applicants list | `GET /api/v1/lab/replays/{jobId}/flips` |
| `compareFlip` | `GET /api/v1/lab/replays/{jobId}/flips/{seq}` |
| `proposeVersion` | `POST /api/v1/lab/versions/{v}/propose` |
| `approveVersion` | `POST /api/v1/lab/versions/{v}/approve` |
| `rejectVersion` | `POST /api/v1/lab/versions/{v}/reject` |
| `rollback` | `POST /api/v1/lab/rollback` |
| `toggleShadow` | `POST /api/v1/lab/versions/{v}/shadow` |
| `shadowHTML` | `GET /api/v1/lab/versions/{v}/shadow-results` |
| lab version cards (`versions`) | `GET /api/v1/lab/versions`, `GET /api/v1/lab/versions/live` |
| version diff (assistant `compareVersions`) | `GET /api/v1/lab/versions/compare?a=&b=` |
| `getPSI` (drift view) | `GET /api/v1/drift/latest` (and `POST /api/v1/drift/run`) |
| drift shift slider | `POST /api/v1/drift/simulate` |
| `verifyUI` | `GET /api/v1/ledger/verify` |
| ledger table + KPIs | `GET /api/v1/ledger`, `GET /api/v1/ledger/stats` |
| `tryUpdate` | `POST /api/v1/ledger/demo/attempt-update` |
| `tamperTest` | `POST /api/v1/ledger/demo/tamper-simulation` |
| System services / circuit | `GET /api/v1/system/status` |
| `setBureau` | `POST /api/v1/system/bureau-fault` |
| System idempotency keys | `GET /api/v1/system/idempotency-keys` |
| System bureau pulls | `GET /api/v1/system/bureau-pulls` |
| `ask` / `askAbout` / `chatItem` | `POST /api/v1/assistant/chat` (assistant-service :8083); tool calls hit `getDecision`, `getReasonCodes`, `getReplayReport`, `compareVersions`, `getOverrideStats`, `getDriftReport` |
| `verifyChain` / `payload` / `hash` (browser FNV) | server SHA-256 hash chain (§5); verify via `GET /api/v1/ledger/verify` |

# Part 6 — Intentional differences

Deviations from the prototype allowed by SPEC §16:

- **LIFECYCLE nav + screens.** The React app adds a LIFECYCLE nav section (Accounts, Collections) in the same sidebar style, and Accounts / Collections screens in the same card language. These do not exist in the prototype (Prompts 19–22).
- **Real authentication.** Login uses the §9 demo emails with password `demo-password` against HTTP Basic auth, instead of the prototype's in-memory user pick. Credentials live in memory only; a page refresh returns to `/login`.
- **SSNs built from §8 digits.** The "Synthetic bureau profile" and "Fraud scenario" selects build a 9-digit SSN per §8 (second digit = profile, third digit = scenario, remaining six random) and are never sent to the API. The "Velocity" scenario keeps the same SSN across submissions ("submission n of 3"); the third within 24 h trips F04.
- **Pipeline timings from the API.** The decide modal's per-step timings and statuses come from the server-measured `pipeline[]` (§3 step 8), not the prototype's hard-coded `ms` values; likewise the ledger uses real SHA-256 hashes, not the browser FNV demo hash.
