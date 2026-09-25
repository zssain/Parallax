# ADR 0003 — Append-only, hash-chained ledger

Status: Accepted · 2026-09-25

## Context

Every decision must be provable after the fact: reproducible from its exact input and detectably unaltered. Two threats matter — the application itself mutating history (bug or misuse) and someone tampering with rows directly.

## Decision

`decision_ledger` is append-only. The runtime role `parallax_app` is granted INSERT and SELECT only — no UPDATE, DELETE or TRUNCATE — so the application physically cannot rewrite a decision. Each row stores `hash = sha256_hex(prevHash + "|" + canonicalPayload)`, chaining to the row before it (genesis prevHash = 64 zeros). To keep hashes stable across round trips, `createdAt` is truncated to microseconds and the canonical payload is built from typed records with alphabetically sorted keys and no whitespace; the verifier re-parses stored jsonb into the same types before hashing.

## Consequences

- Grants stop the application; the hash chain detects anyone who edits a row out of band — the break surfaces at the tampered seq and every link after it.
- Deterministic canonical JSON means reproduce and verify agree years later, regardless of serializer quirks.
- Known limit: a database superuser could rebuild the entire chain from a tampered row. Mitigation (future work): periodically anchor the head hash somewhere outside the database.
- Corrections are new rows (OVERRIDE, REDECISION), never edits — the original decision is preserved forever.
