package com.parallax.application.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.ZoneOffset;

/**
 * The configured "as-of" date that keeps time-window logic working on any calendar date (SPEC §2,
 * invariant 10). Resolution order: {@code parallax.as-of} if set; else the UTC date of the newest
 * decision_ledger row; else today (UTC). Not cached — one indexed query per call.
 */
@Component
public class AsOfDate {

    private final JdbcTemplate jdbc;
    private final String configured;

    public AsOfDate(JdbcTemplate jdbc, @Value("${parallax.as-of:}") String configured) {
        this.jdbc = jdbc;
        this.configured = configured;
    }

    public LocalDate get() {
        if (configured != null && !configured.isBlank()) {
            return LocalDate.parse(configured.trim());
        }
        java.sql.Date maxDate = jdbc.queryForObject(
                "SELECT (max(created_at) AT TIME ZONE 'UTC')::date FROM decision_ledger", java.sql.Date.class);
        return maxDate != null ? maxDate.toLocalDate() : LocalDate.now(ZoneOffset.UTC);
    }
}
