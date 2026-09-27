package com.parallax.application.jobs;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Publishes outbox events to account-service (SPEC §13). A scheduled fixed-delay task, guarded by a
 * Postgres advisory lock so only one instance runs it, takes the oldest unpublished events (batches of
 * 50, fewer than 10 attempts) and POSTs each payload to {@code /internal/v1/accounts} with the internal
 * token and 2 s timeouts. A 2xx marks it published; anything else increments attempts and records a
 * PII-free error, logging once when an event finally gives up at 10 attempts. account-service is
 * idempotent by applicationId, so an at-least-once delivery is safe.
 */
@Component
public class OutboxPublisher {

    /** Advisory lock key for the outbox publisher (SPEC §13). */
    private static final long PUBLISH_LOCK_KEY = 727278L;
    private static final int BATCH_SIZE = 50;
    private static final int MAX_ATTEMPTS = 10;
    private static final Duration TIMEOUT = Duration.ofSeconds(2);
    private static final Logger log = LoggerFactory.getLogger(OutboxPublisher.class);

    private static final String SELECT_PENDING = """
            SELECT id, aggregate_id, payload FROM outbox
            WHERE published_at IS NULL AND attempts < %d
            ORDER BY id ASC
            LIMIT %d
            """.formatted(MAX_ATTEMPTS, BATCH_SIZE);

    private final JdbcTemplate jdbcTemplate;
    private final RestClient restClient;
    private final Clock clock;

    public OutboxPublisher(JdbcTemplate jdbcTemplate, RestClient.Builder builder, Clock clock,
                           @Value("${parallax.accounts.url:http://localhost:8084}") String accountsUrl,
                           @Value("${parallax.internal-token:internal-dev}") String internalToken) {
        this.jdbcTemplate = jdbcTemplate;
        this.clock = clock;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(TIMEOUT);
        factory.setReadTimeout(TIMEOUT);
        this.restClient = builder
                .baseUrl(accountsUrl)
                .defaultHeader("X-Internal-Token", internalToken)
                .requestFactory(factory)
                .build();
    }

    @Scheduled(fixedDelayString = "${parallax.outbox.publish-ms:5000}")
    void scheduled() {
        runOnce();
    }

    /** Publish one batch under the advisory lock; returns the aggregate ids successfully published. */
    public List<String> runOnce() {
        return JobAdvisoryLock.runGuarded(jdbcTemplate, PUBLISH_LOCK_KEY, this::publish);
    }

    private List<String> publish() {
        List<Event> batch = jdbcTemplate.query(SELECT_PENDING, (rs, i) ->
                new Event(rs.getLong("id"), rs.getString("aggregate_id"), rs.getString("payload")));
        List<String> published = new ArrayList<>();
        for (Event event : batch) {
            try {
                post(event.payload());
                jdbcTemplate.update("UPDATE outbox SET published_at = ? WHERE id = ?",
                        Timestamp.from(Instant.now(clock)), event.id());
                published.add(event.aggregateId());
            } catch (RuntimeException e) {
                recordFailure(event, describe(e));
            }
        }
        return published;
    }

    private void post(String payloadJson) {
        restClient.post()
                .uri("/internal/v1/accounts")
                .contentType(MediaType.APPLICATION_JSON)
                .body(payloadJson)
                .retrieve()
                .toBodilessEntity();
    }

    private void recordFailure(Event event, String error) {
        Integer attempts = jdbcTemplate.queryForObject(
                "UPDATE outbox SET attempts = attempts + 1, last_error = ? WHERE id = ? RETURNING attempts",
                Integer.class, error, event.id());
        if (attempts != null && attempts >= MAX_ATTEMPTS) {
            log.error("Outbox event {} gave up after {} attempts ({})", event.id(), MAX_ATTEMPTS, error);
        }
    }

    /** A short, PII-free description of a delivery failure for the last_error column. */
    private static String describe(RuntimeException e) {
        if (e instanceof HttpStatusCodeException http) {
            return "HTTP " + http.getStatusCode().value();
        }
        if (e instanceof ResourceAccessException) {
            return "connection error";
        }
        return e.getClass().getSimpleName();
    }

    private record Event(long id, String aggregateId, String payload) {
    }
}
