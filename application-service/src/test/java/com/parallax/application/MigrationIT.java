package com.parallax.application;

import com.parallax.application.json.CanonicalJson;
import com.parallax.engine.config.RuleConfigs;
import com.parallax.engine.model.RuleConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MigrationIT extends AbstractPostgresIT {

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    CanonicalJson canonicalJson;

    @Test
    void bothVersionsSeededWithExactlyOneLive() {
        Integer count = jdbc.queryForObject("SELECT count(*) FROM rule_version", Integer.class);
        assertThat(count).isEqualTo(2);

        List<String> live = jdbc.queryForList(
                "SELECT version FROM rule_version WHERE status = 'LIVE'", String.class);
        assertThat(live).containsExactly("v1.3");
    }

    @Test
    void storedConfigDeserializesToTheCanonicalRuleConfigs() {
        assertThat(readConfig("v1.3")).isEqualTo(RuleConfigs.v1_3());
        assertThat(readConfig("v1.2")).isEqualTo(RuleConfigs.v1_2());
    }

    @Test
    void configHashMatchesRecomputation() {
        assertRecomputedHashMatches("v1.3");
        assertRecomputedHashMatches("v1.2");
    }

    private RuleConfig readConfig(String version) {
        String json = jdbc.queryForObject(
                "SELECT config FROM rule_version WHERE version = ?", String.class, version);
        return canonicalJson.read(json, RuleConfig.class);
    }

    private void assertRecomputedHashMatches(String version) {
        String storedHash = jdbc.queryForObject(
                "SELECT config_hash FROM rule_version WHERE version = ?", String.class, version);
        String recomputed = canonicalJson.sha256Hex(canonicalJson.write(readConfig(version)));
        assertThat(recomputed).isEqualTo(storedHash);
    }
}
