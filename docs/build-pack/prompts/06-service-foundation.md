# Prompt 06 of 23 — application-service foundation: database, security, PII

## Context

`application-service` (port 8080) is Parallax's orchestrator and the service the web UI talks to most. Before it can take applications it needs a database with least-privilege roles, the seeded rule versions, authentication with the demo users the UI logs in as, encryption for PII, log masking and a consistent error format. This prompt builds exactly that foundation. Prompt 07 adds intake.

## Session rules

1. `git log --oneline -3`, `./mvnw -q -B verify`, `docker compose up -d postgres`. Red → stop.
2. Read `AGENTS.md`, `docs/SPEC.md` §2, §9, §14 (tables application, idempotency\_key, bureau\_pull, rule\_version), §15 (`GET /api/v1/me`), and `docs/DECISIONS.md`. Read parallax-engine's public API; reuse `RuleConfig`, `RuleConfigs`, `RuleConfigValidator`.
3. Table and column names come from SPEC §14 exactly. No extra tables or columns.
4. Build exactly this. Real output only.

## Build

### 1. Dependencies (application-service)

`spring-boot-starter-web`, `-validation`, `-data-jpa`, `-jdbc`, `-security`, `-actuator`, `org.flywaydb:flyway-core`, `flyway-database-postgresql`, `org.postgresql:postgresql`, `springdoc-openapi-starter-webmvc-ui`, `parallax-engine`. Test: `spring-boot-starter-test`, `spring-security-test`, `org.testcontainers:postgresql`, `org.testcontainers:junit-jupiter`.

### 2. Configuration

- `application.yml`: datasource `jdbc:postgresql://localhost:5432/parallax` as `parallax_app`; `spring.flyway.user=parallax_owner` (+ password, same URL); `spring.jpa.hibernate.ddl-auto=validate`; `spring.jpa.open-in-view=false`; Jackson `write-dates-as-timestamps: false`.
- `application-dev.yml`: demo users (below), `PARALLAX_DATA_KEY` and `PARALLAX_TOKEN_KEY` dev defaults — generate two random 32-byte keys once with `openssl rand -base64 32`, paste them with a comment `# DEV ONLY — never use in production`; Swagger enabled.
- Keys are read from env first. Missing keys in a non-dev profile → fail at startup with a clear message.

### 3. Database — Flyway

- `V1__base.sql`: tables `application`, `idempotency_key`, `bureau_pull`, `rule_version` exactly as SPEC §14, indexes as listed, and `CREATE SEQUENCE application_public_seq START 1041`. End with explicit grants: application SELECT, INSERT, UPDATE; idempotency\_key SELECT, INSERT, UPDATE, DELETE; bureau\_pull SELECT, INSERT; rule\_version SELECT, INSERT, UPDATE, DELETE; `GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA public TO parallax_app`.
- `V2__seed_rule_versions` as a **Java** migration (`db.migration.V2__seed_rule_versions extends BaseJavaMigration`): builds JSON from `RuleConfigs.v1_2()` and `v1_3()` using `CanonicalJson` (below), computes `config_hash = sha256Hex(canonical json)`, and inserts v1.2 (RETIRED, created 2026-06-02, retired 2026-08-14) and v1.3 (LIVE, created and promoted 2026-08-14), both created\_by “Aditi Rao”, approved\_by “Vikram Nair”, notes from SPEC §4. Never hand-type a hash.
- `CanonicalJson` (package `com.parallax.application.json`): one shared `ObjectMapper` configured with `SORT_PROPERTIES_ALPHABETICALLY`, `ORDER_MAP_ENTRIES_BY_KEYS`, no indentation, `JavaTimeModule`, dates as ISO strings. Methods `String write(Object)`, `<T> T read(String, Class<T>)`, `String sha256Hex(String)`. This is the only serializer allowed for hashes (config\_hash now, ledger hashes in Prompt 08).
- `LiveRuleVerifier` (`ApplicationRunner`): loads the LIVE rule\_version, deserializes it into `RuleConfig`, runs `RuleConfigValidator` (must be empty), recomputes config\_hash, and fails startup on any mismatch with a clear message.

### 4. Security

- `SecurityConfig`: HTTP Basic, stateless sessions, CSRF disabled (stateless API with Basic auth), `AuthenticationEntryPoint` and `AccessDeniedHandler` returning ProblemDetail JSON (401 / 403). Keep every URL rule in this one class, grouped in the order of SPEC §15, with a comment per rule. Rules now: `/actuator/health` permitAll; `/swagger-ui/**`, `/v3/api-docs/**` permitAll in dev only; `GET /api/v1/me` authenticated; everything else denied (later prompts add rules here).
- Users from `parallax.users` config (list of `{username, displayName, role, passwordEnv?}`) loaded into an `InMemoryUserDetailsManager` with BCrypt-encoded passwords; defaults exactly SPEC §9 (password `demo-password` from env `DEMO_PASSWORD`; assistant from `ASSISTANT_PASSWORD`, default `assistant-dev`). A `CurrentUser` helper exposes username, displayName, role.
- `GET /api/v1/me` → `{username, displayName, role}`.

### 5. PII protection (package `com.parallax.application.pii`)

- `DataCipher`: AES-256-GCM, random 12-byte IV prepended to the ciphertext, 128-bit tag. `byte[] encrypt(String)`, `String decrypt(byte[])`, `String preview(byte[])` → `"enc:v1:" + first 12 hex of the ciphertext + "…"`.
- `EncryptedStringConverter implements AttributeConverter<String, byte[]>` using `DataCipher` (a Spring-managed converter bean).
- `Tokenizer`: HMAC-SHA256 hex with PARALLAX\_TOKEN\_KEY; `ssnToken(ssn)`, `emailHash(email)` (lowercased, trimmed), `phoneHash(phone)` (digits only).
- `NameMasker.mask("Priya Sharma")` → “P•••• S•••••” (each word: first letter + “•” × max(2, length − 1)).
- `PiiMaskingConverter` (Logback `CompositeConverter`) wired in `logback-spring.xml` for every appender: replaces 9-digit runs with `*********`, emails with `***@***`, ISO dates `\d{4}-\d{2}-\d{2}` with `****-**-**`.

### 6. Errors

`ApiExceptionHandler` (`@RestControllerAdvice`): `MethodArgumentNotValidException` → 400 ProblemDetail with `fieldErrors: [{field, message}]`; `ConstraintViolationException` → 400; a `ApiProblem` runtime exception carrying status + title + detail for business errors (409, 422, 404). Unknown exceptions → 500 ProblemDetail without stack traces or PII.

### 7. Integration-test base

`AbstractPostgresIT`: a static Testcontainers `PostgreSQLContainer("postgres:16")` started once, with `docker/postgres/init.sql` copied to `/docker-entrypoint-initdb.d/init.sql` (so the roles exist), `@DynamicPropertySource` wiring the datasource as parallax\_app and Flyway as parallax\_owner, test keys, and profile `dev`. Every later `*IT` in this service extends it.

## Tests

- `MigrationIT`: both versions seeded; exactly one LIVE (v1.3); stored config deserializes to objects equal to `RuleConfigs.v1_3()`/`v1_2()`; config\_hash matches a recomputation.
- `GrantsIT`: as parallax\_app, `CREATE TABLE x(i int)` fails (no CREATE on schema).
- `LiveRuleVerifierTest`: a tampered config hash → startup exception message names the version.
- `SecurityIT`: `GET /api/v1/me` without credentials → 401 ProblemDetail; with aditi.rao@parallax.dev / demo-password → 200 `{username, displayName:"Aditi Rao", role:"STRATEGIST"}`; wrong password → 401; an unknown path → 403 or 401 (never 200).
- `DataCipherTest`: round trip; two encryptions of the same text differ; tampered byte → exception.
- `TokenizerTest`, `NameMaskerTest` (“Priya Sharma”, “Al Li” → “A•• L••”).
- `LogMaskingTest` (`OutputCaptureExtension`): logging “ssn 912345678 mail a@b.com dob 1996-04-18” outputs none of those values.

## Definition of Done (real output)

1. `./mvnw -B verify` green (paste the application-service test totals).
2. Start the app with profile dev (`./mvnw -pl application-service spring-boot:run -Dspring-boot.run.profiles=dev`), then `curl -s -u aditi.rao@parallax.dev:demo-password localhost:8080/api/v1/me` → paste; `curl -s -o /dev/null -w '%{http_code}' localhost:8080/api/v1/me` → 401.
3. `docker compose exec postgres psql -U parallax_owner -d parallax -c 'select version, status, config_hash from rule_version'` → paste.
4. Tick 06. Commit `PX-6: application-service foundation, security, PII, rule versions`. REPORT.

## Do not

Build intake, the bureau client, the ledger or any §15 endpoint other than `/me`.
