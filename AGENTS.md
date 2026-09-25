# Parallax — instructions for AI coding agents and humans

## What Parallax is
A credit card decisioning platform. Decide (pure deterministic engine) → Record (append-only,
hash-chained decision ledger with the exact engine input and rule version) → Replay (Strategy Lab
re-runs history under candidate rule versions). Headline feature: Strategy Lab. All data is synthetic.

## Modules
| Module | Port | Role |
| --- | --- | --- |
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
- [x] 01 Foundation  - [x] 02 Spec  - [x] 03 Engine model  - [x] 04 Engine
- [ ] 05 Bureau  - [ ] 06 Service foundation  - [ ] 07 Intake  - [ ] 08 Ledger
- [ ] 09 Decide end to end  - [ ] 10 Resilience  - [ ] 11 Review queue  - [ ] 12 Synthetic history
- [ ] 13 Replay  - [ ] 14 Governance  - [ ] 15 Shadow, drift, overview  - [ ] 16 Assistant
- [ ] 17 MCP, evals, skills  - [ ] 18 CLI policy, outbox  - [ ] 19 Account service
- [ ] 20 Web shell  - [ ] 21 Web workspace  - [ ] 22 Web strategy + lifecycle  - [ ] 23 Release
