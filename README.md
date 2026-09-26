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

## Seeding

The `seed` profile ingests synthetic history (SEED ledger rows with known loan outcomes) and then
pushes the 14 demo applicants of SPEC §16 through the real pipeline (LIVE, APP-1041 … APP-1054), then
exits. Start Postgres, `bureau-mock` and `decision-service`, then run the one-shot command:

```bash
./mvnw -q -pl application-service spring-boot:run \
  -Dspring-boot.run.profiles=dev,seed \
  -Dspring-boot.run.arguments=--parallax.seed.generate-count=20000
```

Use `--parallax.seed.generate-count=100000` for a larger history. Seeding refuses to run unless the
ledger holds nothing but GOVERNANCE rows; to reseed, reset the volume with
`docker compose down -v && docker compose up -d postgres` first. The generator is also a standalone
CLI:

```bash
java -jar data-generator/target/data-generator-*.jar \
  --count 100000 --seed 20260925 --out history.jsonl --days 365 --drift 0.35 --drift-days 60 --as-of 2026-09-25
```

## Layout
- `parallax-engine/` — pure deterministic engine (zero runtime dependencies)
- `bureau-contract/`, `bureau-mock/` — SOAP credit bureau contract and mock
- `decision-service/`, `application-service/`, `assistant-service/` — Spring Boot services
- `data-generator/` — synthetic history CLI
- `docs/` — SPEC, DECISIONS, and the approved UI under `docs/design/`

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
