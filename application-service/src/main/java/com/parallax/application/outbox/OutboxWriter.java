package com.parallax.application.outbox;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Writes {@code ACCOUNT_OPEN_REQUESTED} events into the transactional outbox (SPEC §13). Propagation is
 * MANDATORY: it must run inside the caller's decision/override transaction, so the event and the ledger
 * row commit together or not at all — the outbox never drifts from the ledger. Only LIVE APPROVED
 * decisions and OVERRIDEs to APPROVED call this; SEED rows never do.
 */
@Component
public class OutboxWriter {

    private static final String EVENT_TYPE = "ACCOUNT_OPEN_REQUESTED";

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public OutboxWriter(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper, Clock clock) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void accountOpenRequested(String applicationId, String displayName, String product,
                                     int creditLimit, int annualIncome, int monthlyHousing, int monthlyDebt,
                                     String ruleVersion, long ledgerSeq) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("applicationId", applicationId);
        payload.put("displayName", displayName);
        payload.put("product", product);
        payload.put("creditLimit", creditLimit);
        payload.put("annualIncome", annualIncome);
        payload.put("monthlyHousing", monthlyHousing);
        payload.put("monthlyDebt", monthlyDebt);
        payload.put("ruleVersion", ruleVersion);
        payload.put("ledgerSeq", ledgerSeq);

        jdbcTemplate.update(
                "INSERT INTO outbox (event_type, aggregate_id, payload, created_at) VALUES (?, ?, ?::jsonb, ?)",
                EVENT_TYPE, applicationId, write(payload), Timestamp.from(Instant.now(clock)));
    }

    private String write(Map<String, Object> payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialize outbox payload", e);
        }
    }
}
