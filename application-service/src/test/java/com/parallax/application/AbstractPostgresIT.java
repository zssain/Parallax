package com.parallax.application;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;

import java.util.Arrays;
import java.util.Base64;

/**
 * Base for every application-service integration test. A single Postgres 16 container is started once
 * and shared; the repo's {@code docker/postgres/init.sql} is copied in so the parallax_owner and
 * parallax_app roles exist, exactly as on a developer machine. The datasource is wired as the
 * least-privilege parallax_app and Flyway as parallax_owner; PII keys and the dev profile are set so
 * the full context (Flyway, security, PII, LiveRuleVerifier) boots as it does in dev.
 */
@SpringBootTest
@ActiveProfiles("dev")
public abstract class AbstractPostgresIT {

    protected static final String TEST_DATA_KEY = Base64.getEncoder().encodeToString(filled((byte) 7));
    protected static final String TEST_TOKEN_KEY = Base64.getEncoder().encodeToString(filled((byte) 9));

    protected static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>(DockerImageName.parse("postgres:16"))
                    .withCopyFileToContainer(
                            MountableFile.forHostPath("../docker/postgres/init.sql"),
                            "/docker-entrypoint-initdb.d/init.sql");

    static {
        POSTGRES.start();
    }

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        String url = "jdbc:postgresql://" + POSTGRES.getHost() + ":" + POSTGRES.getMappedPort(5432) + "/parallax";
        // Runtime as parallax_app (least privilege); Flyway as parallax_owner (owns the schema).
        registry.add("spring.datasource.url", () -> url);
        registry.add("spring.datasource.username", () -> "parallax_app");
        registry.add("spring.datasource.password", () -> "app-dev");
        registry.add("spring.flyway.url", () -> url);
        registry.add("spring.flyway.user", () -> "parallax_owner");
        registry.add("spring.flyway.password", () -> "owner-dev");
        // Several distinct test contexts stay cached at once, each with its own pool, against one shared
        // Postgres — keep pools small so they never exhaust the container's connection slots.
        registry.add("spring.datasource.hikari.maximum-pool-size", () -> "4");
        registry.add("spring.datasource.hikari.minimum-idle", () -> "0");
        // Deterministic 32-byte test keys.
        registry.add("parallax.data-key", () -> TEST_DATA_KEY);
        registry.add("parallax.token-key", () -> TEST_TOKEN_KEY);
        // Push scheduled job triggers far out so tests drive runOnce() deterministically. Both the
        // initial delay AND the fixed delay must be pushed out: without an initial delay a fixedDelay
        // job still fires once at each context's startup, and with many cached contexts sharing one
        // Postgres that lets a startup RedecisionJob process another test's leftover BUREAU_UNAVAILABLE
        // rows against the bureau and trip this context's circuit breaker (intermittent REFER flakes).
        registry.add("parallax.jobs.redecision-ms", () -> "3600000");
        registry.add("parallax.jobs.engine-retry-ms", () -> "3600000");
        registry.add("parallax.outbox.publish-ms", () -> "3600000");
        registry.add("parallax.jobs.initial-delay-ms", () -> "3600000");
        registry.add("parallax.outbox.initial-delay-ms", () -> "3600000");
    }

    private static byte[] filled(byte value) {
        byte[] bytes = new byte[32];
        Arrays.fill(bytes, value);
        return bytes;
    }
}
