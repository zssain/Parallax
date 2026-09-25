package com.parallax.application.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;

/** The {@code idempotency_key} table (SPEC §7, §14). */
@Entity
@Table(name = "idempotency_key")
@IdClass(IdempotencyKeyId.class)
public class IdempotencyKeyEntity {

    @Id
    @Column(name = "client_id")
    private String clientId;

    @Id
    @Column(name = "idem_key")
    private String idemKey;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "request_hash", nullable = false, length = 64)
    private String requestHash;

    @Enumerated(EnumType.STRING)
    @Column(name = "state", nullable = false, length = 16)
    private IdempotencyState state;

    @Column(name = "response_status")
    private Integer responseStatus;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "response_body")
    private String responseBody;

    @Column(name = "application_public_id")
    private String applicationPublicId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    protected IdempotencyKeyEntity() {
    }

    public IdempotencyState getState() {
        return state;
    }

    public String getRequestHash() {
        return requestHash;
    }

    public Integer getResponseStatus() {
        return responseStatus;
    }

    public String getResponseBody() {
        return responseBody;
    }

    public String getApplicationPublicId() {
        return applicationPublicId;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    /** Mark the key COMPLETED with the stored response (called from {@code complete}). */
    public void markCompleted(int responseStatus, String responseBody, String applicationPublicId) {
        this.state = IdempotencyState.COMPLETED;
        this.responseStatus = responseStatus;
        this.responseBody = responseBody;
        this.applicationPublicId = applicationPublicId;
    }
}
