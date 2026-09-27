# How Parallax was built with an AI coding agent

Parallax was built with [Claude Code](https://claude.com/claude-code) driving the implementation and a
human reviewing every step. The point of this document is not that "an AI wrote it" but *how the work was
structured so an AI could write it safely* — the same structure a team would want for any high-stakes
codebase (a decisioning platform that must be reproducible and auditable).

## The prompt series

The build is a fixed sequence of 23 prompts under `docs/build-pack/prompts/` (00–23), each a self-contained
unit of work: foundation, spec, the pure engine, the SOAP bureau, the services, resilience, the review
queue, synthetic history, Strategy Lab replay, governance, drift, the assistant, MCP + evals + skills, the
CLI policy and outbox, account-service, and the web app. Each prompt states its context, session rules
("`git log`, `./mvnw verify`, red → stop"), the exact build steps, the tests to write, and a **Definition
of Done that demands real output** — not "it should work" but "paste the psql row / the curl output / the
green build". One prompt is one commit (`PX-<n>: …`).

Working in a fixed series matters: the agent never has to guess scope. It implements *exactly* the current
prompt, and "no extra features, endpoints, tables or libraries" is a standing rule.

## The contract files

Three files are the contract, and they override the model's own habits:

- **`AGENTS.md`** (and `CLAUDE.md`, which just includes it) — conventions and a numbered list of
  **invariants that must never be violated**: the engine is pure (no Spring, no I/O, no clock, no
  randomness); the ledger is append-only; a decision and its ledger row commit in one transaction; never
  log PII; age is never a scoring factor; adverse-action notices come from a template, never an LLM; the
  assistant is read-only *by credentials, not by prompt*. These are enforced in code and tests
  (`parallax-engine/src/test/java/com/parallax/engine/PurityArchTest.java` fails the build if the engine
  imports Spring or `java.time`).
- **`docs/SPEC.md`** — the authoritative behaviour and API contract. Prompts say "implement SPEC §N
  exactly", and the worked examples in §4 are reproduced as unit tests, so the spec is executable, not
  aspirational.
- **`docs/DECISIONS.md`** — a one-line-per-decision log. Whenever the spec leaves something open, the agent
  picks the simplest option consistent with the spec and records it here with the prompt number and the
  reason. This is where the design rationale lives; it is also where mistakes and their fixes are recorded.

## Skills for the next change

`skills/<name>/SKILL.md` (linked at `.claude/skills/`) teach an agent how to make a *specific kind* of
change safely, referencing real files: `add-scorecard-attribute`, `add-policy-rule`, `run-strategy-replay`.
`scripts/check-skill-paths.sh` fails if any path a skill cites no longer exists, so the guides cannot rot.

## The review loop

Every prompt ends the same way: `./mvnw -B verify` green (unit + Testcontainers integration tests), then
the human runs the prompt's Definition-of-Done checks and reads the diff. "Never invent numbers — report
only what you ran" is a rule, so performance figures, coverage and counts in the reports are measured, not
guessed (see the README performance section, which names the machine and the job id). The eval set
(`evals/assistant-evals.yaml`) applies the same discipline to the assistant: 15 questions checked against
live data, never against numbers the model asserted.

## A mistake the AI made and how it was caught

During **Prompt 10** (resilience), the first implementation of the engine-outage path was wrong in a way
that would have quietly stranded applicants. When the decision engine was unavailable after all retries,
that version stored a **503** response under the request's idempotency key. Because idempotent replay
returns the *stored* response for a repeated key (SPEC §7), the applicant would get the same 503 forever —
and, worse, the engine-retry job would never be able to finish the application, because from the client's
point of view the request had already "completed" with an error.

The review caught it against two invariants: an application that reaches the engine step is already safely
persisted, and the retry job (SPEC §6) is supposed to finish it. The fix was to return **202
`ENGINE_PENDING`** instead — the application is stored, the client is told to poll
`GET /api/v1/applications/{id}`, and the `EngineRetryJob` completes it (or moves it to
`ENGINE_FAILED_MANUAL` after three attempts). The corrected behaviour shipped in commit **`d5a2ec7`**
("PX-10: resilience, engine pending path, re-decision jobs") and the decision is recorded in
`docs/DECISIONS.md` (`2026-09-26 · P10 · an engine outage after retries returns 202 ENGINE_PENDING, not
503`), which explicitly labels the 503 approach "the v1 bug". `EnginePendingIT` now guards the corrected
path so the mistake cannot return.
