package com.parallax.application.shadow;

import com.parallax.application.error.ApiProblem;
import com.parallax.application.security.CurrentUser;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.List;

/** Toggles shadow mode for a version and reads its disagreements table (SPEC §10, §15). */
@Service
public class ShadowService {

    private final JdbcTemplate jdbc;
    private final CurrentUser currentUser;
    private final Clock clock;

    public ShadowService(JdbcTemplate jdbc, CurrentUser currentUser, Clock clock) {
        this.jdbc = jdbc;
        this.currentUser = currentUser;
        this.clock = clock;
    }

    /** Enable (only REPLAYED/PROPOSED, replacing any other) or disable shadow for a version. */
    @Transactional
    public ShadowViews.ShadowToggle setShadow(String version, boolean enabled) {
        String status = status(version);
        if (enabled) {
            if (!"REPLAYED".equals(status) && !"PROPOSED".equals(status)) {
                throw new ApiProblem(HttpStatus.CONFLICT, "Conflict",
                        "Only a REPLAYED or PROPOSED version can run in shadow mode (" + version
                                + " is " + status + ")");
            }
            jdbc.update("UPDATE shadow_config SET version = ?, enabled_by = ?, enabled_at = ? WHERE id = 1",
                    version, currentUser.displayName(), Timestamp.from(Instant.now(clock)));
        } else {
            jdbc.update("UPDATE shadow_config SET version = NULL, enabled_by = NULL, enabled_at = NULL"
                    + " WHERE id = 1 AND version = ?", version);
        }
        return new ShadowViews.ShadowToggle(version, enabled);
    }

    @Transactional(readOnly = true)
    public ShadowViews.ShadowResults results(String version) {
        List<ShadowViews.Item> items = jdbc.query("""
                SELECT a.public_id, dl.outcome AS live_outcome, dl.credit_limit AS live_limit,
                       sr.outcome AS shadow_outcome, sr.credit_limit AS shadow_limit, sr.agrees
                FROM shadow_result sr
                JOIN decision_ledger dl ON dl.seq = sr.ledger_seq
                JOIN application a ON a.id = dl.application_id
                WHERE sr.version = ?
                ORDER BY sr.ledger_seq DESC
                """, (rs, i) -> new ShadowViews.Item(
                        rs.getString("public_id"),
                        new ShadowViews.Side(rs.getString("live_outcome"),
                                rs.getObject("live_limit", Integer.class)),
                        new ShadowViews.Side(rs.getString("shadow_outcome"),
                                rs.getObject("shadow_limit", Integer.class)),
                        rs.getBoolean("agrees")),
                version);
        long disagreements = items.stream().filter(item -> !item.agrees()).count();
        return new ShadowViews.ShadowResults(items.size(), disagreements, items);
    }

    private String status(String version) {
        try {
            return jdbc.queryForObject("SELECT status FROM rule_version WHERE version = ?", String.class, version);
        } catch (EmptyResultDataAccessException e) {
            throw new ApiProblem(HttpStatus.NOT_FOUND, "Not Found", "No rule version " + version);
        }
    }
}
