# Decisions log

Each entry is one line: `YYYY-MM-DD · P<nn> · decision · reason`.
`P<nn>` is the prompt number that made the decision.

2026-09-25 · P01 · spring-boot-starter-parent 3.5.16 · newest stable 3.x on Maven Central (metadata <release> is 4.2.0-M2, a 4.x milestone, excluded)
2026-09-25 · P01 · testcontainers-bom 1.21.4 · newest stable 1.x on Maven Central
2026-09-25 · P01 · spring-ai-bom 1.1.8 · newest 1.x GA on Maven Central (metadata <release> is 2.1.0-M1, a milestone, excluded)
2026-09-25 · P01 · resilience4j-spring-boot3 2.4.0 · newest stable 2.x on Maven Central
2026-09-25 · P01 · springdoc-openapi-starter-webmvc-ui 2.9.1 · newest stable 2.x; the 2.9.x line targets Spring Boot 3.5.x
2026-09-25 · P01 · jqwik 1.10.1 · newest stable 1.x on Maven Central
2026-09-25 · P01 · archunit-junit5 1.5.1 · newest stable 1.x on Maven Central
2026-09-25 · P01 · wiremock-standalone 3.13.2 · newest stable 3.x on Maven Central
2026-09-25 · P01 · maven-enforcer-plugin 3.6.3 · newest stable 3.x; surefire and failsafe versions inherited from spring-boot-starter-parent
2026-09-25 · P01 · Spring Boot app classes named by full module CamelCase (BureauMockApplication, DecisionServiceApplication, ApplicationServiceApplication, AssistantServiceApplication) · avoids the awkward ApplicationApplication; packages remain com.parallax.{bureau,decision,application,assistant}
2026-09-25 · P01 · spring-boot-starter-test added (test scope) to the four Spring Boot modules · required by the @SpringBootTest context-load tests the prompt asks for
2026-09-25 · P01 · spring-boot-maven-plugin bound only in the four Spring Boot modules · plain-jar modules (engine, contract, generator) stay plain
2026-09-25 · P01 · Built 4 context-load tests + 2 placeholder tests (6 total) · only four Spring Boot services exist in this prompt (account-service is Prompt 19), so the prompt's "five context-load tests" appears to be a miscount; count reported honestly
2026-09-25 · P02 · SPEC.md reproduced verbatim; unescaped Markdown-only escapes (\_ \[ \]) but kept table-cell pipes as \| · preserves every value while rendering as clean Markdown
2026-09-25 · P02 · Added an H1 title + one-line orientation above §1 in SPEC.md · a spec file needs a title; no rule, value, endpoint or field was added or changed
2026-09-25 · P02 · ADRs placed in docs/adr/ · matches the prompt heading and the DoD check `ls docs/adr`
2026-09-25 · P02 · UI-INVENTORY.md uses `#` for the six required parts and `##` for each screen · satisfies the DoD `grep '^#'` (six parts + one heading per screen)
2026-09-26 · P03 · Usd.format uses the Unicode minus sign (−, U+2212) for negatives · matches the prototype money() output and SPEC formatting ("−$100")
2026-09-26 · P03 · RuleConfigValidator negative-point and sum-of-maxima checks run regardless of array length; only ordering checks are skipped on a wrong length · SPEC only requires skipping ordering to avoid index errors
2026-09-26 · P03 · ban-frameworks enforcer bans org.springframework*/jakarta.*/com.fasterxml.jackson* on compile+runtime scope only · keeps archunit/jqwik/junit on the test classpath while blocking runtime frameworks
2026-09-26 · P03 · Removed parallax-engine EnginePlaceholderTest · superseded by real engine tests (ReasonCodeTest, EngineInputTest, UsdTest, RuleConfigsTest, RuleConfigValidatorTest, PurityArchTest)
2026-09-26 · P04 · Property "reasonCodes empty iff APPROVED" asserted as APPROVED⇒empty plus "empty & not-APPROVED ⇒ fraud REFER" · the one SPEC corner is a perfect 850 score referred by a fraud flag, which has no failed policy and no lost points (documented by a dedicated example test)
2026-09-26 · P04 · Added parallax-engine/src/test/resources/junit-platform.properties (jqwik.tries.default=1000, jqwik.reporting.onlyFailures=false) · surfaces the per-property tries/checks summary in build output
2026-09-26 · P04 · Added SegmentsTest covering scoreBand boundaries · Segments is new production code introduced this prompt and deserves a boundary test
2026-09-26 · P05 · jaxb2-maven-plugin 3.3.0 (newest stable 3.x) · generates Jakarta XML Binding classes compatible with Spring Boot's jakarta.xml.bind-api/jaxb-runtime 4.0.x
2026-09-26 · P05 · WebServiceConfig implements WsConfigurer (not the deprecated WsConfigurerAdapter) and calls PayloadValidatingInterceptor.afterPropertiesSet() by hand · the interceptor is built with new (not a Spring bean), so its schema validator must be compiled explicitly
2026-09-26 · P05 · bureau-mock keeps spring-boot-starter-web alongside spring-boot-starter-web-services · provides the servlet container + MVC for the REST FaultController and actuator
2026-09-26 · P05 · BureauEndpointTest scenario cases use rule-consistent SSNs (918345678 SSN_BEFORE_DOB, 919345678 DECEASED) · SPEC §8's reference SSNs 912845678/912945678 are internally inconsistent with the stated third-digit rule; Prompt 05 says implement scenario from the third digit, so the rule is authoritative
2026-09-26 · P06 · Removed the placeholder ApplicationServiceApplicationTest (plain @SpringBootTest contextLoads) · the new Testcontainers ITs (SecurityIT, MigrationIT, GrantsIT) boot the full context, and a plain @SpringBootTest would need a database in the Surefire phase where none is available
2026-09-26 · P06 · rule_version seed sets proposed_by = created_by ("Aditi Rao") for both versions and v1.2 promoted_at = its created_at (2026-06-02) · the prompt fixes only created_by/approved_by/notes/status/dates; these nullable columns get maker-checker-consistent values so later governance prompts have realistic data
2026-09-26 · P06 · PII log masking is wired in logback-spring.xml with an identical logback-test.xml mirror · Spring Boot processes logback-spring.xml only when its logging system starts, so plain unit tests (LogMaskingTest) load logback-test.xml to exercise the same converter
2026-09-26 · P06 · Runtime/Flyway DB passwords default to the docker/postgres/init.sql dev values (app-dev, owner-dev) with env overrides PARALLAX_APP_PASSWORD/PARALLAX_OWNER_PASSWORD · the prompt fixes the roles and URL but not the local passwords
2026-09-26 · P06 · CurrentUser resolves displayName and role from parallax.users by username, not from the authenticated principal · InMemoryUserDetailsManager returns a plain Spring User, dropping any custom UserDetails subclass fields
2026-09-26 · P07 · IdempotencyService.begin uses INSERT … ON CONFLICT DO NOTHING then reads the row in the same REQUIRES_NEW transaction · a caught constraint violation would poison the transaction, so ON CONFLICT keeps the concurrency race resolvable in one transaction (exactly one 202, one 409)
2026-09-26 · P07 · char(n) columns (ssn_token, ssn_last4, email_hash, phone_hash, request_hash) mapped with @JdbcTypeCode(SqlTypes.CHAR) and explicit length · ddl-auto=validate rejects the default String→varchar(255) mapping against Postgres bpchar
2026-09-26 · P07 · velocity SQL casts (:createdAt)::timestamptz and types the nullable email/phone params (Types.VARCHAR) · an untyped bind parameter in ":createdAt - interval" is inferred as interval, and a null hash still needs a SQL type
2026-09-26 · P07 · idempotent replay of a 201/202 creation returns 200 (other stored statuses replay unchanged) · reconciles SPEC §7's literal "200 iff stored was 201" with this prompt's 202 intake response, which the prompt's test and DoD require to replay as 200
2026-09-26 · P07 · response_body is stored as jsonb, so a replayed body is value-identical but key-order/whitespace-normalized · the idempotency IT compares parsed JSON rather than the raw string
2026-09-26 · P07 · intake() is @Transactional (application + bureau_pull rows) while idempotency begin/complete/abandon stay REQUIRES_NEW · matches SPEC §7 and lets abandon delete the key even when the intake transaction rolls back (Prompt 09 folds complete into the decision transaction)
