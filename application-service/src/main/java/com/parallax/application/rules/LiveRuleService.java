package com.parallax.application.rules;

import com.parallax.application.json.CanonicalJson;
import com.parallax.engine.model.RuleConfig;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.concurrent.atomic.AtomicReference;

/** Supplies the LIVE rule version and config (SPEC §4). Cached; {@link #evict()} refreshes it (Prompt 14). */
@Service
public class LiveRuleService {

    private final JdbcTemplate jdbcTemplate;
    private final CanonicalJson canonicalJson;
    private final AtomicReference<LiveRule> cache = new AtomicReference<>();

    public LiveRuleService(JdbcTemplate jdbcTemplate, CanonicalJson canonicalJson) {
        this.jdbcTemplate = jdbcTemplate;
        this.canonicalJson = canonicalJson;
    }

    public LiveRule current() {
        LiveRule cached = cache.get();
        if (cached != null) {
            return cached;
        }
        LiveRule loaded = jdbcTemplate.queryForObject(
                "SELECT version, config, promoted_at, created_at FROM rule_version WHERE status = 'LIVE'",
                (rs, i) -> {
                    OffsetDateTime promoted = rs.getObject("promoted_at", OffsetDateTime.class);
                    OffsetDateTime created = rs.getObject("created_at", OffsetDateTime.class);
                    Instant since = (promoted != null ? promoted : created).toInstant();
                    return new LiveRule(rs.getString("version"),
                            canonicalJson.read(rs.getString("config"), RuleConfig.class), since);
                });
        cache.set(loaded);
        return loaded;
    }

    /** The config stored for a specific version (used by reproduce). */
    public RuleConfig configOf(String version) {
        String json = jdbcTemplate.queryForObject(
                "SELECT config FROM rule_version WHERE version = ?", String.class, version);
        return canonicalJson.read(json, RuleConfig.class);
    }

    /** Clear the cache so the next {@link #current()} reloads (Prompt 14 promote/rollback). */
    public void evict() {
        cache.set(null);
    }
}
