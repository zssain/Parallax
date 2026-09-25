# ADR 0001 — A pure engine library

Status: Accepted · 2026-09-25

## Context

Parallax must reproduce any past decision exactly and replay history under candidate rules. If the live decision path and the replay/reproduce path ran different code, "identical" would be a hope, not a guarantee. Impurity — a clock, randomness, I/O, framework magic — is what makes code produce different answers for the same input.

## Decision

The decision logic lives in `parallax-engine`, a plain Java library with zero runtime dependencies: no Spring, no I/O (java.io, java.nio.file, java.net, java.sql), no clock, no randomness. Live decisions (via decision-service), reproduce, and Strategy Lab replay all call the same artifact. Purity is enforced mechanically — ArchUnit tests forbid the banned packages, and the Maven enforcer plugin pins Java and Maven versions — not by reviewer discipline.

## Consequences

- Same input + same rule config = same output, always; reproduce and replay are trustworthy.
- Time, bureau calls and persistence are the orchestrator's job (application-service), keeping the engine testable in isolation.
- Any accidental Spring or I/O import fails the build, so the invariant cannot rot silently.
- The engine cannot log or fetch anything; callers must pass everything it needs as typed input.
