package com.parallax.account.cli;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.parallax.account.api.AccountViews.CliRequestView;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;

/**
 * Reads and writes {@code cli_request} rows (SPEC §14). Uses {@link JdbcTemplate} because the reasons
 * column is jsonb; the list is stored and read as JSON.
 */
@Component
public class CliRequestStore {

    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    public CliRequestStore(JdbcTemplate jdbc, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    public void insert(long accountId, int requestedLimit, String outcome, int newLimit,
                       List<String> reasons, boolean applied, String createdBy, Instant createdAt) {
        jdbc.update("INSERT INTO cli_request (account_id, requested_limit, outcome, new_limit, reasons, "
                        + "applied, created_by, created_at) VALUES (?, ?, ?, ?, ?::jsonb, ?, ?, ?)",
                accountId, requestedLimit, outcome, newLimit, write(reasons), applied, createdBy,
                Timestamp.from(createdAt));
    }

    /** CLI history for an account, newest first. */
    public List<CliRequestView> listByAccount(long accountId) {
        return jdbc.query(
                "SELECT requested_limit, outcome, new_limit, reasons, applied, created_by, created_at "
                        + "FROM cli_request WHERE account_id = ? ORDER BY created_at DESC, id DESC",
                (rs, i) -> new CliRequestView(
                        rs.getInt("requested_limit"),
                        rs.getString("outcome"),
                        rs.getInt("new_limit"),
                        read(rs.getString("reasons")),
                        rs.getBoolean("applied"),
                        rs.getString("created_by"),
                        rs.getObject("created_at", java.time.OffsetDateTime.class).toInstant()),
                accountId);
    }

    private String write(List<String> reasons) {
        try {
            return objectMapper.writeValueAsString(reasons);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialize CLI reasons", e);
        }
    }

    private List<String> read(String json) {
        try {
            return objectMapper.readValue(json, new com.fasterxml.jackson.core.type.TypeReference<List<String>>() {
            });
        } catch (Exception e) {
            throw new IllegalStateException("Failed to parse CLI reasons", e);
        }
    }
}
