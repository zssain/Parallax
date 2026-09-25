# ADR 0002 — Synchronous orchestration

Status: Accepted · 2026-09-25

## Context

A credit application touches several steps — idempotency, bureau pull, fraud screen, engine, ledger commit. A common reflex is to wire these together with a message broker. At this scope, that would add operational weight (Kafka, a schema registry, consumer groups) and make the flow harder to read, test and reason about, for no real benefit.

## Decision

Orchestrate the live decision flow synchronously inside application-service: one request runs validation → idempotency → bureau → features → engine → ledger, and the ledger row and the decision commit in the same transaction. Use asynchronous messaging only where a second service genuinely needs an event: account opening is delivered through a transactional outbox, written in the same transaction as the APPROVED decision and published by a poller. No Kafka, no Redis, no API gateway.

## Consequences

- The decision path is easy to follow, test end-to-end, and debug; latency is bounded by a 2 s bureau timeout behind a circuit breaker.
- Exactly-once-ish delivery to account-service comes from the outbox plus idempotency by applicationId, without a broker.
- If throughput ever demanded it, the outbox is the natural seam to introduce real messaging later.
- Cross-service work that is not decision-critical (e.g. account open) is eventually consistent, by design.
