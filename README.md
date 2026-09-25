# Parallax

A credit card decisioning platform built around three ideas: **Decide** — one pure,
deterministic engine (same input + same rule config = same output, always); **Record** —
every decision lands in an append-only, hash-chained ledger with its exact normalized input
and rule version, so any decision can be reproduced; **Replay** — the Strategy Lab re-runs the
whole history under a candidate rule version before it ships, with an honest impact report.
All data is synthetic.

Status: under construction — see the [AGENTS.md](AGENTS.md) progress log.

## Prerequisites
- Java 21
- Maven 3.9+ (a wrapper, `./mvnw`, is included)
- Docker Desktop (for local PostgreSQL 16)

## Build
```
./mvnw verify
```

## Local database
```
cp .env.example .env
docker compose up -d postgres
```

## Layout
- `parallax-engine/` — pure deterministic engine (zero runtime dependencies)
- `bureau-contract/`, `bureau-mock/` — SOAP credit bureau contract and mock
- `decision-service/`, `application-service/`, `assistant-service/` — Spring Boot services
- `data-generator/` — synthetic history CLI
- `docs/` — SPEC, DECISIONS, and the approved UI under `docs/design/`
