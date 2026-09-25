package com.parallax.application.rules;

import com.parallax.application.json.CanonicalJson;
import com.parallax.engine.config.RuleConfigValidator;
import com.parallax.engine.model.RuleConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * On startup, loads the LIVE rule version, deserializes its stored config into a {@link RuleConfig},
 * validates it (must be clean) and recomputes its config_hash from the canonical JSON. Any mismatch
 * fails startup with a message that names the offending version, so a tampered or corrupt LIVE
 * config can never silently decide applicants.
 */
@Component
public class LiveRuleVerifier implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(LiveRuleVerifier.class);

    private final JdbcTemplate jdbcTemplate;
    private final CanonicalJson canonicalJson;

    public LiveRuleVerifier(JdbcTemplate jdbcTemplate, CanonicalJson canonicalJson) {
        this.jdbcTemplate = jdbcTemplate;
        this.canonicalJson = canonicalJson;
    }

    @Override
    public void run(ApplicationArguments args) {
        List<Map<String, Object>> live = jdbcTemplate.queryForList(
                "SELECT version, config, config_hash FROM rule_version WHERE status = 'LIVE'");
        if (live.isEmpty()) {
            throw new IllegalStateException("No LIVE rule version found; the seed migration must run first");
        }
        for (Map<String, Object> row : live) {
            verify(String.valueOf(row.get("version")),
                    String.valueOf(row.get("config")),
                    String.valueOf(row.get("config_hash")));
        }
        log.info("Verified {} LIVE rule version(s)", live.size());
    }

    /**
     * Deserialize, validate and hash-check one stored rule version. Throws {@link IllegalStateException}
     * naming {@code version} on any validation error or config_hash mismatch. Package-visible so it can
     * be unit-tested without a database.
     */
    void verify(String version, String configJson, String storedHash) {
        RuleConfig config = canonicalJson.read(configJson, RuleConfig.class);

        List<String> errors = RuleConfigValidator.validate(config);
        if (!errors.isEmpty()) {
            throw new IllegalStateException(
                    "LIVE rule version " + version + " failed validation: " + String.join("; ", errors));
        }

        String recomputed = canonicalJson.sha256Hex(canonicalJson.write(config));
        if (!recomputed.equals(storedHash)) {
            throw new IllegalStateException("LIVE rule version " + version
                    + " config_hash mismatch: stored " + storedHash + " but recomputed " + recomputed);
        }
    }
}
