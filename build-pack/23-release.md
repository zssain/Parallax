# Prompt 23 of 23 — Release: Docker, end-to-end tests, README, v1.0.0

## Context

Everything is built. This prompt makes Parallax runnable by anyone with one command, proves it end to end in a real browser, and packages it for a portfolio and an interview: containers for every service, a full `docker compose up` with seeded data and the 14 demo applications, a lifecycle demo script, Playwright E2E tests, CI workflows, a Jira import file, the Bitbucket mirror, a complete README with real screenshots and measured numbers, and a final consistency pass so the docs match the code. Run it in two sessions if needed (A: sections 1–3; B: sections 4–7), committing after each.

## Session rules

1. `git log --oneline -3`, `./mvnw -q -B verify`, `cd web && npm run build && npm run lint`. Red → stop.
2. Read `AGENTS.md`, `docs/SPEC.md` (all), `docs/DECISIONS.md`, `README.md`.
3. Verify image tags and action versions exist before using them.
4. Screenshots and numbers must be real captures and real measurements. Real output only.

## Build

### 1. Containers

- One root `Dockerfile` with `ARG MODULE`: builder stage `eclipse-temurin:21-jdk` copies the wrapper and POMs first (dependency layer cache), then sources, runs `./mvnw -q -B -DskipTests -pl $MODULE -am package`; runtime stage `eclipse-temurin:21-jre` with `curl` installed for health checks, a non-root user, the module's jar, `ENTRYPOINT ["java","-jar","/app/app.jar"]`.
- `web/Dockerfile`: `node:20` build (`npm ci && npm run build`) → `nginx:alpine` serving `dist/` with `web/nginx.conf` that proxies exactly like the Vite dev proxy (`/api/v1/assistant` → assistant-service:8083, `/api/v1/accounts` and `/api/v1/collections` → account-service:8084, other `/api` → application-service:8080) and falls back to `index.html` for client routes.

### 2. docker-compose.yml (complete)

Services: `postgres`; `bureau-mock` (profile dev); `decision-service`; `seeder` (application-service image, profiles `dev,seed`, `--parallax.seed.generate-count=${SEED_COUNT:-20000}`, `restart: "no"`); `application-service` (dev); `assistant-service` (`ANTHROPIC_API_KEY: ${ANTHROPIC_API_KEY:-}`); `account-service` (dev); `web` on host port 5173. Health checks with `curl -fs http://localhost:<port>/actuator/health`. Dependencies: seeder waits for postgres, bureau-mock and decision-service healthy; application-service waits for `seeder` `service_completed_successfully`; account-service waits for application-service healthy; web waits for the three API services. All secrets via `.env` with dev defaults in `.env.example` (`POSTGRES_PASSWORD`, `INTERNAL_TOKEN`, `DEMO_PASSWORD`, `ASSISTANT_PASSWORD`, `PARALLAX_DATA_KEY`, `PARALLAX_TOKEN_KEY`, `ANTHROPIC_API_KEY`, `SEED_COUNT`). Service URLs point at compose service names.

### 3. Demo script — `scripts/demo-lifecycle.sh`

After `docker compose up`: wait until `GET /api/v1/accounts` (as priya) returns at least 2 accounts; on the first, simulate 6 on-time months then request a CLI of limit + $1,000 (print the result); on the second, simulate 2 months with a missed payment (print `GET /api/v1/collections`). Idempotent enough to re-run (it only adds months). README documents it.

### 4. End-to-end tests — `web/e2e/` (Playwright, Chromium, baseURL `http://localhost:5173`)

Against the running compose stack:

1. Marketing site loads; the five-question check reaches a result; “Open workspace” goes to login.
2. Each of the four demo users signs in and sees the correct role in the sidebar.
3. Priya submits a prime application (Prime profile, None scenario, default financials) → APPROVED in the pipeline modal → opens the decision → Reproduce shows identical.
4. APP-1041 decision detail shows DECLINED 445 and the adverse action notice lists the four reasons.
5. Idempotency: submit, submit again with the same key → the idempotent-replay toast.
6. Priya approves the oldest REFER in the review queue with O2 and a note → the queue shrinks, the ledger shows an OVERRIDE row.
7. Aditi creates v1.4 (cutoff 700), replays, sees the report, proposes; Aditi's approve is denied; Vikram approves → LIVE; Vikram rolls back.
8. Ledger: Verify chain ok; Attempt UPDATE shows “permission denied”; Tamper test shows a break.
9. System: outage → a new application is REFER with “circuit OPEN” → restore → the toast lists it as re-decided.
10. Drift: the slider at 1.2 shows a SIMULATION PSI higher than the latest.
11. Assistant: renders; if configured, “Why was APP-1041 declined?” returns an answer with tool chips; otherwise the not-configured card is visible.
12. Accounts: at least one account exists; Collections page renders its four buckets.

`npm run e2e` runs them. Also capture README screenshots with Playwright into `docs/screenshots/` (1440 × 900, light theme): marketing hero, login, overview, new application with the pipeline modal, decision detail, review queue, strategy lab report, drift, ledger, assistant, system, accounts, collections; plus a dark-theme overview.

### 5. CI

- Keep `.github/workflows/ci.yml` (unit + integration) and add a `web` job (`npm ci`, `npm run lint`, `npm run build`).
- `.github/workflows/e2e.yml`: `workflow_dispatch` + nightly: `docker compose up -d --build`, wait for health, `npx playwright install --with-deps chromium`, `npm run e2e`, upload the Playwright report; always `docker compose down -v`.
- `.github/workflows/evals.yml`: `workflow_dispatch`: compose up, run the eval runner with secret `ANTHROPIC_API_KEY`, upload `evals/results/`.
- `bitbucket-pipelines.yml` still matches the Maven build; add a step for the web build.

### 6. Jira and Bitbucket

- `docs/jira-import.csv` (Summary, Issue Type, Epic Link/Parent, Description, Labels) with epics Phase 1 (Foundation), Phase 2 (Core), Phase 2 stretch, Phase 3 and one story per prompt PX-1…PX-23, each description a two-line summary of what the prompt delivered, matching the commit messages. README explains how to import it into a free Jira board with key PX.
- `docs/bitbucket.md`: create an empty Bitbucket repo, `git remote add bitbucket <url>`, `git push bitbucket --all && git push bitbucket --tags`, enable Pipelines; optional GitHub Action mirror using a repository secret (documented, not enabled).

### 7. README and final consistency pass

- README sections: pitch (Decide · Record · Replay, “Two views. One decision.”); the problem and the solution; architecture (Mermaid of the services and databases); a feature list mapped to the job description (table: requirement → where); run it (`cp .env.example .env && docker compose up --build`, URLs, the four demo logins, `scripts/demo-lifecycle.sh`); a 5-minute demo script following the E2E flow; Performance (the Prompt 13 numbers with machine specs and date); Testing (a table of layers with the **real** test counts from the latest Surefire/Failsafe reports and Playwright); AI-assisted development (AGENTS.md, CLAUDE.md, skills/, docs/ai-workflow.md, the MCP server, evals); screenshots; limitations (synthetic data, dev auth, no real bureau, single-node jobs); interview talking points (reject inference, PD × EAD × LGD, transactional audit, reproducibility, age not scored, safe AI, the ledger's superuser limit and head-hash anchoring).
- Consistency: AGENTS.md, `.github/copilot-instructions.md`, SPEC and README must match the code (endpoints, ports, roles, tables). Fix drift and list every fix. `grep -rin hindsight .` (excluding `node_modules` and `target`) must print nothing. `grep -rn "localStorage\|sessionStorage" web/src` must print nothing. No `TODO` left except the author TODO in ai-workflow.md if it exists.

## Definition of Done (real output)

1. `./mvnw -B verify` green; `npm run lint && npm run build` green.
2. From a clean state (`docker compose down -v`): `docker compose up -d --build`; paste `docker compose ps` with every service healthy (seeder exited 0) and the time it took.
3. `scripts/demo-lifecycle.sh` output.
4. `npm run e2e` summary (all passed, or the exact failures and why).
5. The consistency-pass fix list and the two grep results.
6. Tick 23. Commit `PX-23: containers, end-to-end tests, CI, README, release`; `git tag v1.0.0`.
7. Final report: every screen with the endpoints it calls; anything not done and why; every DECISIONS.md entry added in this prompt.

## Do not

Fake screenshots or numbers, disable failing tests, or add features.
