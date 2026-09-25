package com.parallax.application.idempotency;

import com.parallax.application.domain.IdempotencyKeyEntity;
import com.parallax.application.domain.IdempotencyKeyId;
import com.parallax.application.domain.IdempotencyKeyRepository;
import com.parallax.application.error.ApiProblem;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.Optional;

/**
 * Concurrency-safe idempotency, exactly SPEC §7. Each operation runs in its own {@code REQUIRES_NEW}
 * transaction so the IN_PROGRESS marker is committed and visible to a racing request. The insert uses
 * {@code INSERT … ON CONFLICT DO NOTHING} so a losing race does not poison the transaction and can
 * read the winning row in the same transaction.
 */
@Service
public class IdempotencyService {

    private static final Duration TTL = Duration.ofHours(24);

    private static final String INSERT = """
            INSERT INTO idempotency_key (client_id, idem_key, request_hash, state, created_at, expires_at)
            VALUES (?, ?, ?, 'IN_PROGRESS', ?, ?)
            ON CONFLICT (client_id, idem_key) DO NOTHING
            """;

    private static final String SELECT = """
            SELECT state, request_hash, response_status, response_body, expires_at
            FROM idempotency_key WHERE client_id = ? AND idem_key = ?
            """;

    private final JdbcTemplate jdbcTemplate;
    private final IdempotencyKeyRepository repository;

    public IdempotencyService(JdbcTemplate jdbcTemplate, IdempotencyKeyRepository repository) {
        this.jdbcTemplate = jdbcTemplate;
        this.repository = repository;
    }

    /**
     * Reserve the key. Empty result → proceed (this request owns the key). A present {@link Replay}
     * → return the stored response. Throws {@link ApiProblem} 409 (in progress) or 422 (hash mismatch).
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Optional<Replay> begin(String clientId, String key, String hash) {
        Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);
        if (tryInsert(clientId, key, hash, now)) {
            return Optional.empty();
        }

        Map<String, Object> row;
        try {
            row = jdbcTemplate.queryForMap(SELECT, clientId, key);
        } catch (EmptyResultDataAccessException vanished) {
            // The conflicting row was deleted between the insert and the read; take the key now.
            tryInsert(clientId, key, hash, now);
            return Optional.empty();
        }

        Instant expiresAt = ((Timestamp) row.get("expires_at")).toInstant();
        if (expiresAt.isBefore(now)) {
            jdbcTemplate.update("DELETE FROM idempotency_key WHERE client_id = ? AND idem_key = ?", clientId, key);
            tryInsert(clientId, key, hash, now);
            return Optional.empty();
        }

        String state = (String) row.get("state");
        if ("COMPLETED".equals(state)) {
            String storedHash = ((String) row.get("request_hash")).trim();
            if (storedHash.equals(hash)) {
                Integer status = (Integer) row.get("response_status");
                Object body = row.get("response_body");
                return Optional.of(new Replay(status, body == null ? null : body.toString()));
            }
            throw new ApiProblem(HttpStatus.UNPROCESSABLE_ENTITY, "Idempotency conflict",
                    "Idempotency-Key reused with a different request body");
        }
        throw new ApiProblem(HttpStatus.CONFLICT, "Request in progress",
                "A request with this Idempotency-Key is still in progress");
    }

    /**
     * Store the final response against the key inside the caller's (decision) transaction, so the
     * ledger row and the completed key commit together (SPEC §3, §7).
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void completeInCurrentTransaction(String clientId, String key, int status, String body,
                                             String applicationPublicId) {
        IdempotencyKeyEntity entity = repository.findById(new IdempotencyKeyId(clientId, key))
                .orElseThrow(() -> new IllegalStateException("Idempotency key missing on complete: " + key));
        entity.markCompleted(status, body, applicationPublicId);
        repository.save(entity);
    }

    /** Delete the key so the client can retry (called when processing throws). */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void abandon(String clientId, String key) {
        repository.findById(new IdempotencyKeyId(clientId, key)).ifPresent(repository::delete);
    }

    private boolean tryInsert(String clientId, String key, String hash, Instant now) {
        int inserted = jdbcTemplate.update(INSERT,
                clientId, key, hash, Timestamp.from(now), Timestamp.from(now.plus(TTL)));
        return inserted == 1;
    }
}
