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
