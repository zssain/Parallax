# Parallax

**Two views. One decision.** A credit-card decisioning platform built around three ideas:
**Decide** — one pure, deterministic engine (same input + same rule config = same output,
always); **Record** — every decision lands in an append-only, hash-chained ledger with its
exact normalized input and rule version, so any decision can be reproduced; **Replay** — the
Strategy Lab re-runs the whole history under a candidate rule version *before it ships*, with
an honest impact report. All data is synthetic.

Status: v1.0.0 — feature-complete (see the [AGENTS.md](AGENTS.md) progress log).

## The problem

Credit strategy teams change decision rules constantly, but they usually cannot (a) see a
change's effect on past applicants *before* shipping it, (b) reproduce an old decision
exactly, or (c) govern who approves a rule change. Parallax is built around those three gaps.

## The solution

Take an application, pull a credit report over SOAP, screen for fraud, run policy rules and a
scorecard, and return **approve / refer / decline** with a limit and reason codes. Every
decision is written to a tamper-evident ledger with its full inputs, so it can be reproduced
exactly and **replayed** under a candidate rule version. Rule changes flow through a
maker-checker workflow (draft → replayed → proposed → live) with one-click rollback, and a
read-only AI assistant explains any of it — grounded only in tool results, read-only by
credentials.

## Architecture

A pure engine packaged as a plain Java library is used *both* by the live service and by the
replay tooling, so "pure and stateless" is enforced by the build, not claimed. Everything with
I/O lives around it.

```mermaid
flowchart LR
  subgraph Client
    W[web · React · 5173]
  end
  W -->|REST /api + Idempotency-Key| A
  A[application-service · 8080<br/>intake, ledger, Strategy Lab, drift] -->|SOAP| B[bureau-mock · 8082]
  A -->|REST /internal| D[decision-service · 8081]
  D --- E[(parallax-engine<br/>pure library)]
  A --- E
  A --> P[(Postgres: parallax<br/>applications, ledger, rule versions)]
  G[assistant-service · 8083<br/>Spring AI agent + MCP] -->|read-only REST| A
  X[account-service · 8084<br/>accounts, statements, collections] -->|REST /internal| A
  X --> Q[(Postgres: accounts)]
  A -.->|transactional outbox<br/>ACCOUNT_OPEN_REQUESTED| X
```

| Module | Port | Role |
| --- | --- | --- |
| parallax-engine | — | Pure library: fraud, policy, scorecard, limits, reason codes, CLI policy |
| bureau-contract / bureau-mock | 8082 | SOAP credit bureau (XSD/JAXB) with fault injection |
| decision-service | 8081 | Thin REST wrapper around the engine |
| application-service | 8080 | Intake, idempotency, ledger, review queue, Strategy Lab, drift, outbox |
| assistant-service | 8083 | Read-only Spring AI agent (7 tools) + MCP server (SSE) |
| account-service | 8084 | Accounts, statements, credit-line increases, collections |
| web | 5173 | React + TypeScript UI |

## Feature map (to the target job description)

| JD requirement | Where in Parallax |
| --- | --- |
| Applied Java | Java 21 across every module |
| Spring / Spring Boot | Every service; Spring Security, Spring Web Services, Spring AI |
| SOAP, REST, JSON, XML | REST APIs under `/api/v1`; SOAP bureau with WSDL, XSD and JAXB mapping |
| Unit and integration tests | JUnit 5, Mockito, jqwik, Testcontainers, WireMock, golden-set replay |
| Continuous integration | GitHub Actions (`.github/workflows/`) and Bitbucket Pipelines |
| Jira, Bitbucket, git | `docs/jira-import.csv`, `docs/bitbucket.md`, `PX-<n>:` commit keys |
| Improve system performance | Strategy Lab replay of 100k decisions, measured and published (below) |
| Front-end UI | React + TypeScript web app, 14 screens |
| Understanding of AI | Read-only underwriter/strategist agent constrained to engine output |
| AI agents, tools, skills, instruction files | Spring AI agent, MCP server, `AGENTS.md`, `skills/`, Copilot instructions |
| Credit and fraud losses | Fraud stage, exposure-weighted expected loss, PSI drift monitoring |
| Acquisition → collections | Intake, decisioning, accounts, delinquency buckets, collections queue |

## Run it (one command)

Prerequisites: Docker Desktop. (For building from source instead: JDK 21 and Node 20+.)

```bash
cp .env.example .env          # optional; dev defaults are baked in
docker compose up --build
```

Boot order is handled for you: Postgres → bureau-mock + decision-service → a one-shot
**seeder** (synthetic history + the 14 demo applications, then exits) → application-service →
assistant-service + account-service → web. First boot builds all images and seeds ~20k rows,
so give it a few minutes. Then open **http://localhost:5173**.

Set `SEED_COUNT=100000` in `.env` to reproduce the performance numbers (slower first boot).
The AI assistant is optional: leave `OPENAI_API_KEY` blank and the assistant shows a
"model not configured" card; set a key (and optionally `PARALLAX_ASSISTANT_MODEL`) to enable chat.

### Demo logins (all password `demo-password`)

| User | Role | Can do |
| --- | --- | --- |
| aditi.rao@parallax.dev | STRATEGIST | Create candidates, run replays, propose promotions |
| vikram.nair@parallax.dev | APPROVER | Approve/reject promotions, roll back |
| priya.menon@parallax.dev | UNDERWRITER | Work the review queue, accounts, collections |
| sam.iyer@parallax.dev | AUDITOR | Read the ledger and decisions (PII masked) |

The login screen has a one-click button for each.

### Service URLs

web http://localhost:5173 · application-service http://localhost:8080 · decision-service
:8081 · bureau-mock :8082 · assistant-service :8083 · account-service :8084. Each service
exposes `/actuator/health`; application-service and the others expose OpenAPI at
`/swagger-ui.html` under the dev profile.

## 5-minute demo script

1. **Marketing → workspace.** Open http://localhost:5173, try the five-question check, click
   *Open workspace* and sign in as **Priya** (underwriter).
2. **Submit an application.** New application → pick the *Prime* profile, *None* scenario,
   defaults → submit → watch the pipeline modal reach **APPROVED** → open the decision →
   **Reproduce** shows *identical*.
3. **A decline with reasons.** Open **APP-1041** (Priya, declined) → score 445, four reason
   codes, and the adverse-action notice.
4. **Idempotency.** Submit once, then again with the same key → the idempotent-replay toast.
5. **Review an override.** Review queue → approve the oldest REFER with code **O2** and a note
   → the queue shrinks and an **OVERRIDE** row appears in the ledger.
6. **Strategy Lab (the headline).** Sign in as **Aditi** → create **v1.4** (approve cutoff
   700) → run the replay → read the impact report (approval rate, flip matrix, expected loss,
   outcome-unknown) → open a flipped applicant → **Propose**. Aditi's *Approve* is denied
   (maker-checker); sign in as **Vikram** and approve → **LIVE** (a GOVERNANCE ledger row) →
   then **roll back**.
7. **Ledger integrity.** Decision ledger → **Verify chain** (intact) → **Attempt UPDATE**
   (permission denied) → **Tamper test** (chain breaks on a copy).
8. **Resilience.** System → simulate a bureau outage → submit an application (REFER, circuit
   OPEN) → restore → the re-decision job clears it.
9. **Drift & lifecycle.** Drift → slider to 1.2 shows a simulated PSI. Then run
   `./scripts/demo-lifecycle.sh` and watch **Accounts** and **Collections** fill in.

### Account lifecycle script

After `docker compose up`, `./scripts/demo-lifecycle.sh` waits for accounts to open, simulates
six on-time months on one account and requests a credit-line increase, then simulates two
missed months on another and prints the collections queue. It only adds months, so it is safe
to re-run. (Config via `ACCOUNT_URL`, `DEMO_PASSWORD`.)

## Build from source (developers)

```bash
./mvnw verify          # all modules: unit + Testcontainers integration tests
cd web && npm ci --legacy-peer-deps && npm run lint && npm run build
```

Postgres for local runs: `docker compose up -d postgres`. Seeding directly (one-shot, then
exits):

```bash
./mvnw -q -pl application-service spring-boot:run \
  -Dspring-boot.run.profiles=dev,seed \
  -Dspring-boot.run.arguments=--parallax.seed.generate-count=20000
```

Seeding refuses to run unless the ledger holds nothing but GOVERNANCE rows; to reseed, reset
the volume with `docker compose down -v` first. The generator is also a standalone CLI:

```bash
java -jar data-generator/target/data-generator-*.jar \
  --count 100000 --seed 20260925 --out history.jsonl --days 365 --drift 0.35 --drift-days 60 --as-of 2026-09-25
```

## Performance

Strategy Lab replay, measured 2026-09-26:

- Machine: Apple M5, 10 cores, 16 GB RAM (`sysctl -n machdep.cpu.brand_string` / `hw.ncpu` / `hw.memsize`).
- Dataset: 100,000 SEED history records plus the 14 demo LIVE decisions → 100,014 replayable rows.
- Candidate: v1.3 config with `approveCutoff` 700; baseline the LIVE v1.3.
- Result: **loadMs 1099, evaluateMs 147, totalMs 1645** (job RJ-0582AD). Baseline approvals
  72,082 (0.7207) → candidate 67,746 (0.6774); 4,336 flips; immature 5.

The engine is CPU-bound and pure, so evaluation runs on a bounded parallel executor sized to
the CPU count; the real cost is loading rows, streamed with a JDBC fetch size and keyset
pagination.

## Testing

Measured by `./mvnw verify` on 2026-09-27: **161 unit tests** (Surefire, including jqwik
property tests) and **114 integration tests** (Failsafe on Testcontainers Postgres 16) across
all modules — **0 failures, 0 errors**. The web app adds Playwright end-to-end specs run against
the running stack with `npm run e2e` — screenshot walkthroughs plus `release.spec.ts` (8 functional
scenarios: marketing, four-role sign-in, submit + reproduce, adverse-action, ledger verify/tamper,
drift simulation, assistant, accounts/collections), all green against `docker compose`.

| Layer | Tool | What it proves |
| --- | --- | --- |
| Engine unit tests | JUnit 5 + AssertJ | Each rule, band and reason-code ranking; worked examples |
| Engine properties | jqwik | Lowering utilization never lowers the score; limit ≤ ATP max; ≤ 4 reason codes; determinism |
| Config validation | JUnit 5 | Overlapping bands, gaps and misordered cutoffs are rejected |
| Service unit tests | Mockito | Orchestration, idempotency branches, state transitions |
| Integration | Testcontainers (Postgres 16) | Transactional audit, ledger grants, hash chain under concurrency, idempotency 409 |
| Bureau | WireMock + Spring WS | Adapter mapping, timeouts, circuit breaker; the real WSDL is exercised |
| Golden set | Failsafe IT | Seeded decisions reproduce identically |
| Log safety | JUnit 5 | A raw SSN never reaches log output |
| Web | Playwright (Chromium) | End-to-end flows across every screen |
| Agent evals | manual / CI (opt-in) | 15 questions answered correctly with the expected tools |

CI runs unit + integration on every push (`.github/workflows/ci.yml`, plus a `web` lint/build
job). E2E (`e2e.yml`) and the LLM evals (`evals.yml`) run on demand / nightly.

## AI-assisted development

Parallax was built with an AI coding agent against a fixed prompt series, kept honest by
contract files the agent must read first:

- [`AGENTS.md`](AGENTS.md) / [`CLAUDE.md`](CLAUDE.md) — module map, conventions and the
  invariants (engine purity, append-only ledger, never log PII, age never scored, assistant
  read-only by credentials).
- [`skills/`](skills/) — repeatable recipes (`add-scorecard-attribute`, `add-policy-rule`,
  `run-strategy-replay`); `scripts/check-skill-paths.sh` checks every path they cite exists.
- [`docs/ai-workflow.md`](docs/ai-workflow.md) — how it was built, including a real mistake the
  AI made and how it was caught.
- The MCP server and the 15-item eval set let an agent query Parallax directly and be scored.

## AI assistant and MCP

`assistant-service` (port 8083) is a read-only Spring AI agent with seven tools (`getDecision`,
`getReasonCodes`, `runReplay`, `getReplayReport`, `compareVersions`, `getOverrideStats`,
`getDriftReport`). Every number in an answer comes from a tool result; masked PII only; no write
tool exists. It needs `OPENAI_API_KEY` to chat (no key → 503), but the tool catalogue and MCP
server run without one.

The same seven tools are exposed over the **Model Context Protocol** (Spring AI MCP server,
WebMVC/SSE transport): server name `parallax`, SSE endpoint `/sse`, message endpoint
`/mcp/message`, behind the same INTERNAL HTTP Basic auth as the chat API. The service calls
application-service with its own ASSISTANT credentials, so MCP clients get read-only access by
construction.

### Use Parallax from Claude Desktop

Claude Desktop speaks stdio, so bridge to the HTTP/SSE endpoint with
[`mcp-remote`](https://www.npmjs.com/package/mcp-remote), passing an `Authorization: Basic …` header for an
INTERNAL demo user (here `priya.menon@parallax.dev:demo-password`, whose Base64 is
`cHJpeWEubWVub25AcGFyYWxsYXguZGV2OmRlbW8tcGFzc3dvcmQ=`). Add to `claude_desktop_config.json`:

```json
{
  "mcpServers": {
    "parallax": {
      "command": "npx",
      "args": [
        "-y", "mcp-remote",
        "http://localhost:8083/sse",
        "--header", "Authorization: Basic cHJpeWEubWVub25AcGFyYWxsYXguZGV2OmRlbW8tcGFzc3dvcmQ="
      ]
    }
  }
}
```

### List the tools over the protocol with curl

The SSE transport replies on the stream, so open `/sse` (which returns a per-session `/mcp/message`
endpoint), then POST the JSON-RPC handshake and `tools/list` to that endpoint:

```bash
AUTH='priya.menon@parallax.dev:demo-password'
# 1) open the stream; the first event carries the session message endpoint
curl -sN -u "$AUTH" http://localhost:8083/sse &      # prints: event:endpoint / data:/mcp/message?sessionId=…
MSG='/mcp/message?sessionId=<from the endpoint event>'
# 2) handshake, then list tools (responses arrive on the stream above)
curl -s -u "$AUTH" -H 'Content-Type: application/json' http://localhost:8083$MSG \
  -d '{"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2024-11-05","capabilities":{},"clientInfo":{"name":"curl","version":"1.0"}}}'
curl -s -u "$AUTH" -H 'Content-Type: application/json' http://localhost:8083$MSG \
  -d '{"jsonrpc":"2.0","method":"notifications/initialized"}'
curl -s -u "$AUTH" -H 'Content-Type: application/json' http://localhost:8083$MSG \
  -d '{"jsonrpc":"2.0","id":2,"method":"tools/list"}'
```

The `tools/list` result on the stream contains exactly the seven tool names above. `McpToolsIT` asserts the
same list by driving a real MCP client through this handshake.

### Assistant eval set

`evals/assistant-evals.yaml` holds 15 questions checked against live data. Run them against a seeded,
running stack (needs `OPENAI_API_KEY` so the assistant is configured, and `PARALLAX_BASE_URL` for
application-service):

```bash
OPENAI_API_KEY=… PARALLAX_BASE_URL=http://localhost:8080 \
  ./mvnw -pl assistant-service verify -Dgroups=evals
```

It resolves each item's fact references from application-service, asks the assistant, checks the tools used
and the facts present, and writes `evals/results/<date>.md`. It is excluded from the default build and is
skipped (never failed) when those env vars are absent.

## Bureau mock

`bureau-mock` (port 8082) is a synthetic SOAP credit bureau. All data is synthetic; a report is
driven entirely by the digits of the 9-digit SSN (which must start with `9`). SOAP endpoint `/ws`,
WSDL `/ws/bureau.wsdl`.

The **second digit** selects the credit profile and the **third digit** selects a scenario (SPEC §8):

| 2nd digit | Profile | utilization / inquiries / delinquencies / tradelines / file-age months |
| --- | --- | --- |
| 0–2, 9 | PRIME | 0.080 / 0 / 0 / 12 / 156 |
| 3–5 | NEAR_PRIME | 0.550 / 3 / 0 / 5 / 40 |
| 6–7 | SUBPRIME | 0.820 / 5 / 2 / 4 / 30 |
| 8 | THIN_FILE | 0.200 / 1 / 0 / 1 / 10 |

| 3rd digit | Scenario | Effect |
| --- | --- | --- |
| 0–6 | NONE | FileAddress = request address, SsnIssuanceYear = birth year + 1, not deceased |
| 7 | ADDRESS_MISMATCH | FileAddress = "14 Old Mill Rd, Dayton OH" |
| 8 | SSN_BEFORE_DOB | SsnIssuanceYear = birth year − 3 |
| 9 | DECEASED | DeceasedIndicator = true |

Run it and make a sample pull (PRIME, no scenario):

```bash
./mvnw -pl bureau-mock spring-boot:run -Dspring-boot.run.profiles=dev
curl -s -H 'Content-Type: text/xml' http://localhost:8082/ws -d '<soap:Envelope xmlns:soap="http://schemas.xmlsoap.org/soap/envelope/" xmlns:br="urn:parallax:bureau:v1"><soap:Body><br:CreditReportRequest><br:Ssn>912345678</br:Ssn><br:FirstName>Ishaan</br:FirstName><br:LastName>Kapoor</br:LastName><br:DateOfBirth>1996-04-18</br:DateOfBirth><br:Address>48 Elm Street, Columbus OH</br:Address><br:PullType>HARD</br:PullType></br:CreditReportRequest></soap:Body></soap:Envelope>'
```

Fault injection (dev profile only): `POST /admin/fault` with `{"mode":"NONE"|"DOWN"|"SLOW","delayMs":int}`.
`DOWN` makes `/ws` return HTTP 503 with a SOAP fault; `SLOW` adds latency.

## Screenshots

Captured from the running app with Playwright into [`docs/screenshots/`](docs/screenshots/)
(1440×900, light theme unless noted): marketing hero, login, overview (light and dark), new
application with the pipeline modal, decision detail, review queue, Strategy Lab report, drift,
ledger, assistant, system, accounts and collections. The `docs/screenshots/compare/` set places
each screen beside the prototype.

## Limitations

Deliberately scoped for a portfolio, not production:

- **Synthetic data only.** SSNs start with `9` (never issued); the bureau is a mock.
- **Dev auth.** In-memory HTTP Basic demo users with dev passwords; no real IdP, no TLS.
- **No real key management.** PII AES-GCM/HMAC keys come from env with dev defaults.
- **Single-node jobs.** Scheduled jobs are guarded by a Postgres advisory lock, not a cluster
  scheduler; no Kafka, Redis or API gateway (synchronous orchestration is enough at this scope).
- **The assistant needs a key** to chat; without one it degrades gracefully.

## Interview talking points

- **Reject inference** — newly approved applicants have no observed outcome, so they are marked
  *outcome unknown* and excluded from observed-loss totals; a clearly-labelled simulated view
  uses the generator's counterfactuals.
- **Honest loss** — expected loss is exposure-weighted, `EL = PD × EAD × LGD` with
  `EAD ≈ limit × CCF`, not a flat default rate; a rule that only moves limits still moves loss.
- **Transactional audit** — a decision and its ledger row commit in one transaction, so an
  unaudited decision cannot exist.
- **Reproducibility** — the ledger stores the full normalized input and rule version; the
  reproduce endpoint re-runs it and a golden set checks it in CI.
- **Age is never scored** — legal capacity (18+) and the under-21 rule are policy checks only.
- **Safe AI** — read-only by credentials (not by prompt), tool outputs framed as data, masked
  PII, a prompt-injection test and evals in CI.
- **Ledger integrity** — SHA-256 hash chain over canonical JSON, inserts serialized by a
  Postgres advisory lock, and the runtime role has INSERT/SELECT only (no UPDATE/DELETE grant).
