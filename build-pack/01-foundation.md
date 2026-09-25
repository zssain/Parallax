# Prompt 01 of 23 — Repository foundation

## Context

You are the coding agent for **Parallax**, a portfolio project built for a Synchrony *API Engineer 1 – Credit* application. Parallax is a credit card decisioning platform with three ideas:

- **Decide**: one pure, deterministic engine. Same input + same rule config = same output, always.
- **Record**: every decision goes into an append-only, hash-chained ledger with its exact normalized input and rule version, so any decision can be reproduced exactly.
- **Replay**: the Strategy Lab re-runs the whole history under a candidate rule version before it ships, with an honest impact report.

Stack: Java 21, Spring Boot 3, Maven multi-module, PostgreSQL 16, Docker Compose, React + TypeScript (later). The machine is macOS with Docker Desktop. The repository is the current directory. It is empty except for `docs/design/prototype.html` (the approved UI, a single HTML file) and possibly `docs/build-pack/`.

This is prompt 01 of 23. Your job: the foundation every later prompt stands on. **Do not implement any business feature in this prompt.**

## Session rules

1. Check preconditions first: `java -version` prints 21; `docker info` succeeds; `docs/design/prototype.html` exists. If any fails, stop and tell me exactly what is missing.
2. Build exactly what this prompt lists. No extra modules, dependencies, endpoints or files.
3. Before relying on any dependency version, verify it resolves from Maven Central (fetch `https://repo1.maven.org/maven2/<group path>/<artifact>/maven-metadata.xml`). Never guess a version.
4. Record every choice this prompt leaves open in `docs/DECISIONS.md` as one line: `YYYY-MM-DD · P01 · decision · reason`.
5. Never invent output, numbers or test counts. Report only what you ran.

## Build

### 1. Layout (create exactly this)

```text
pom.xml                      parent, packaging pom
mvnw, mvnw.cmd, .mvn/        Maven wrapper (mvn -N wrapper:wrapper, Maven 3.9.x)
parallax-engine/             plain Java library — ZERO runtime dependencies
bureau-contract/             plain jar; XSD + JAXB arrive in Prompt 05
bureau-mock/                 Spring Boot, port 8082
decision-service/            Spring Boot, port 8081
application-service/         Spring Boot, port 8080
assistant-service/           Spring Boot, port 8083
data-generator/              plain Java CLI jar
docker/postgres/init.sql
docker-compose.yml
.env.example                 POSTGRES_PASSWORD=parallax-dev
.github/workflows/ci.yml
.github/copilot-instructions.md
bitbucket-pipelines.yml
AGENTS.md  CLAUDE.md  README.md
docs/SPEC.md                 placeholder line only; Prompt 02 writes it
docs/DECISIONS.md
docs/design/README.md
skills/.gitkeep
.gitignore  .editorconfig
```

`account-service/` (port 8084) and `web/` (port 5173) are created in Prompts 19 and 20. Do not create them now.

### 2. Parent POM

- groupId `com.parallax`, version `0.1.0-SNAPSHOT`, `maven.compiler.release` 21, UTF-8.
- Parent: `org.springframework.boot:spring-boot-starter-parent`, the newest **3.x** release on Maven Central (check the metadata; not 4.x).
- `<modules>` lists the seven modules above.
- `dependencyManagement` imports these BOMs / pins these versions (newest stable of the stated major that resolves; log each in DECISIONS.md):
  - `org.testcontainers:testcontainers-bom` 1.x
  - `org.springframework.ai:spring-ai-bom` 1.x GA (not milestone)
  - `io.github.resilience4j:resilience4j-spring-boot3` 2.x
  - `org.springdoc:springdoc-openapi-starter-webmvc-ui` 2.x (compatible with the chosen Boot version)
  - `net.jqwik:jqwik` 1.x, `com.tngtech.archunit:archunit-junit5` 1.x, `org.wiremock:wiremock-standalone` 3.x
- Plugins: `maven-enforcer-plugin` (requireJavaVersion `[21,)`, requireMavenVersion `[3.9,)`); `maven-surefire-plugin` runs `**/*Test.java`; `maven-failsafe-plugin` runs `**/*IT.java` in `verify`.

### 3. Module skeletons

- Each Spring Boot module: `com.parallax.<name>.<Name>Application` (packages: `com.parallax.bureau`, `com.parallax.decision`, `com.parallax.application`, `com.parallax.assistant`), deps `spring-boot-starter-web` + `spring-boot-starter-actuator`, `application.yml` with `server.port` and `management.endpoints.web.exposure.include: health,info`, and a `*ApplicationTest` that loads the context. application-service has no datasource yet; exclude DataSource auto-config is not needed because no JPA starter is added yet.
- `parallax-engine`: package `com.parallax.engine`, JUnit 5 + AssertJ test scope only, one placeholder test.
- `data-generator`: package `com.parallax.generator`, a `Main` that prints usage, one placeholder test.
- `bureau-contract`: empty `src/main/resources/xsd/` with a `README.md` line “XSD arrives in Prompt 05”.

### 4. Docker

`docker-compose.yml`, service `postgres`: image `postgres:16`, port `5432:5432`, `POSTGRES_PASSWORD: ${POSTGRES_PASSWORD:-parallax-dev}`, named volume `pgdata`, init script mounted at `/docker-entrypoint-initdb.d/init.sql`, healthcheck `pg_isready -U postgres` every 5s. Leave commented placeholders for the other services.

`docker/postgres/init.sql` (dev-only passwords, say so in a comment at the top):

```sql
CREATE ROLE parallax_owner LOGIN PASSWORD 'owner-dev';
CREATE ROLE parallax_app   LOGIN PASSWORD 'app-dev';
CREATE ROLE accounts_owner LOGIN PASSWORD 'owner-dev';
CREATE ROLE accounts_app   LOGIN PASSWORD 'app-dev';
CREATE DATABASE parallax OWNER parallax_owner;
CREATE DATABASE accounts OWNER accounts_owner;
\connect parallax
ALTER SCHEMA public OWNER TO parallax_owner;
REVOKE ALL ON SCHEMA public FROM PUBLIC;
GRANT USAGE ON SCHEMA public TO parallax_app;
\connect accounts
ALTER SCHEMA public OWNER TO accounts_owner;
REVOKE ALL ON SCHEMA public FROM PUBLIC;
GRANT USAGE ON SCHEMA public TO accounts_app;
```

Do **not** add `ALTER DEFAULT PRIVILEGES`: every migration grants table privileges explicitly, so the ledger can stay INSERT/SELECT-only.

### 5. CI

- `.github/workflows/ci.yml`: on push and pull\_request; `ubuntu-latest`; `actions/setup-java` temurin 21 with Maven cache; `./mvnw -B verify`.
- `bitbucket-pipelines.yml`: image `maven:3.9-eclipse-temurin-21`; `definitions.services.docker`; default step runs `./mvnw -B verify` with `services: [docker]` and env `TESTCONTAINERS_RYUK_DISABLED=true`.

### 6. AGENTS.md — write this content (adjust nothing except formatting)

```markdown
# Parallax — instructions for AI coding agents and humans

## What Parallax is
A credit card decisioning platform. Decide (pure deterministic engine) → Record (append-only,
hash-chained decision ledger with the exact engine input and rule version) → Replay (Strategy Lab
re-runs history under candidate rule versions). Headline feature: Strategy Lab. All data is synthetic.

## Modules
| Module | Port | Role |
| parallax-engine | — | Pure library: fraud, policy, scorecard, limits, reason codes, CLI policy |
| bureau-contract | — | SOAP XSD + generated JAXB classes |
| bureau-mock | 8082 | SOAP credit bureau with fault injection |
| decision-service | 8081 | Thin REST wrapper around the engine |
| application-service | 8080 | Intake, idempotency, orchestration, ledger, review queue, Strategy Lab, drift |
| assistant-service | 8083 | Read-only Spring AI agent + MCP server |
| data-generator | — | Synthetic history CLI + library |
| account-service | 8084 | Accounts, CLI, collections (Prompt 19) |
| web | 5173 | React UI (Prompt 20) |

## Invariants (never violate)
1. parallax-engine is pure: no Spring, no I/O (java.io, java.nio.file, java.net, java.sql), no clock, no randomness.
2. decision_ledger is append-only. Never write UPDATE or DELETE against it. The runtime role has INSERT and SELECT only.
3. A decision and its ledger row commit in the same transaction.
4. Never log PII: SSN, date of birth, full name, email, phone, address.
5. Age is never a scoring factor. Legal capacity (18+) and under-21 are policy checks only.
6. Adverse action notices come from a deterministic template, never from an LLM.
7. The AI assistant is read-only by credentials, not by prompt.
8. A rule version used by any decision is immutable.
9. The UI matches docs/design/prototype.html. It is the source of truth for layout, copy and styling.
10. Time-window logic uses the configured as-of date (parallax.as-of), never a bare now(), for drift and trends.

## Ground rules for agents
- Read AGENTS.md, docs/SPEC.md and docs/DECISIONS.md before changing code.
- Implement exactly what the current prompt specifies. No extra features, services, endpoints, tables, fields or libraries.
- Missing detail → simplest option consistent with docs/SPEC.md + one line in docs/DECISIONS.md.
- Verify dependency versions resolve before using them.
- Never invent numbers (performance, coverage, test counts). Report only what you measured or ran.
- Run the tests you write. Never finish with failing, skipped or @Disabled tests.
- Keep changes to earlier modules minimal and list them in your report.
- Every task ends with ./mvnw -B verify green.

## Conventions
- Packages com.parallax.<module>. REST under /api/v1 (public) and /internal/v1 (service to service).
- JSON camelCase. Money in whole US dollars (account-service uses cents, suffixed Cents). Timestamps UTC Instant, truncated to microseconds.
- Errors are RFC 7807 ProblemDetail; validation errors add fieldErrors [{field, message}].
- Flyway V<n>__<desc>.sql; never edit an applied migration; every migration ends with explicit GRANTs.
- Unit tests *Test (Surefire); integration tests *IT (Failsafe, Testcontainers Postgres 16).

## Git and Jira
Jira key PX. Branches feature/PX-<n>-<slug>. Commits start with "PX-<n>: ".

## Progress log
- [ ] 01 Foundation  - [ ] 02 Spec  - [ ] 03 Engine model  - [ ] 04 Engine
- [ ] 05 Bureau  - [ ] 06 Service foundation  - [ ] 07 Intake  - [ ] 08 Ledger
- [ ] 09 Decide end to end  - [ ] 10 Resilience  - [ ] 11 Review queue  - [ ] 12 Synthetic history
- [ ] 13 Replay  - [ ] 14 Governance  - [ ] 15 Shadow, drift, overview  - [ ] 16 Assistant
- [ ] 17 MCP, evals, skills  - [ ] 18 CLI policy, outbox  - [ ] 19 Account service
- [ ] 20 Web shell  - [ ] 21 Web workspace  - [ ] 22 Web strategy + lifecycle  - [ ] 23 Release
```

`CLAUDE.md` contains exactly:

```markdown
@AGENTS.md

AGENTS.md is the source of truth for conventions and invariants. docs/SPEC.md is the source of truth for behaviour. docs/design/prototype.html is the source of truth for the UI.
```

`.github/copilot-instructions.md`: the Invariants and Conventions sections, condensed to bullets.

### 7. Other files

- `docs/SPEC.md`: one line, “Written in Prompt 02.”
- `docs/DECISIONS.md`: title `# Decisions log`, a line explaining the format, then your entries.
- `docs/design/README.md`: “prototype.html is the approved UI. Open it in a browser. Every web screen must match it; React replaces its in-browser simulation with real API calls.”
- `.gitignore`: `target/`, `node_modules/`, `dist/`, `.env`, `.idea/`, `*.iml`, `.DS_Store`, `web/test-results/`, `web/playwright-report/`.
- `.editorconfig`: UTF-8, LF, 4 spaces for Java/XML, 2 for TS/JSON/YAML/MD.
- `README.md`: title, one-paragraph pitch (Decide · Record · Replay), “Status: under construction — see the AGENTS.md progress log”, prerequisites, `./mvnw verify`, `docker compose up -d postgres`.

## Tests

The five context-load tests and two placeholder tests. Nothing else.

## Definition of Done (show real output for each)

1. `./mvnw -B verify` → BUILD SUCCESS (paste the Reactor Summary).
2. `cp .env.example .env && docker compose up -d postgres`, then `docker compose ps` shows healthy.
3. `docker compose exec postgres psql -U parallax_owner -d parallax -c 'select current_user'` and the same for `accounts_owner -d accounts` both succeed.
4. Tick 01 in the AGENTS.md progress log.
5. `git add -A && git commit -m "PX-1: foundation, agent instructions, CI"`.
6. Final report in the REPORT format: files, versions chosen, DECISIONS.md entries.

## Do not

Add JPA, security, Flyway, business code, account-service or web/. Modify `docs/design/prototype.html`.
