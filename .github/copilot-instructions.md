# Copilot instructions for Parallax

Parallax is a credit card decisioning platform: Decide (pure deterministic engine) → Record
(append-only, hash-chained ledger) → Replay (Strategy Lab). All data is synthetic. See AGENTS.md
for the full contract; docs/SPEC.md for behaviour; docs/design/prototype.html for the UI.

## Invariants (never violate)
- parallax-engine is pure: no Spring, no I/O (java.io, java.nio.file, java.net, java.sql), no clock, no randomness.
- decision_ledger is append-only: never UPDATE or DELETE it; the runtime role has INSERT and SELECT only.
- A decision and its ledger row commit in the same transaction.
- Never log PII: SSN, date of birth, full name, email, phone, address.
- Age is never a scoring factor; legal capacity (18+) and under-21 are policy checks only.
- Adverse action notices come from a deterministic template, never from an LLM.
- The AI assistant is read-only by credentials, not by prompt.
- A rule version used by any decision is immutable.
- The UI matches docs/design/prototype.html (source of truth for layout, copy, styling).
- Time-window logic uses the configured as-of date (parallax.as-of), never a bare now().

## Conventions
- Packages com.parallax.<module>. REST under /api/v1 (public) and /internal/v1 (service to service).
- JSON camelCase. Money in whole US dollars (account-service uses cents, suffixed Cents). Timestamps UTC Instant, truncated to microseconds.
- Errors are RFC 7807 ProblemDetail; validation errors add fieldErrors [{field, message}].
- Flyway V<n>__<desc>.sql; never edit an applied migration; every migration ends with explicit GRANTs.
- Unit tests *Test (Surefire); integration tests *IT (Failsafe, Testcontainers Postgres 16).
- Build exactly what the current prompt specifies — no extra features, endpoints, tables or libraries.
- Never invent numbers; report only what you ran. Every task ends with ./mvnw -B verify green.
