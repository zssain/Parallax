package com.parallax.application.rules;

import com.parallax.application.json.CanonicalJson;
import com.parallax.engine.config.RuleConfigs;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LiveRuleVerifierTest {

    private final CanonicalJson canonicalJson = new CanonicalJson();
    // No JdbcTemplate needed: verify() is exercised directly.
    private final LiveRuleVerifier verifier = new LiveRuleVerifier(null, canonicalJson);

    @Test
    void validConfigWithMatchingHashPasses() {
        String json = canonicalJson.write(RuleConfigs.v1_3());
        String hash = canonicalJson.sha256Hex(json);
        assertThatCode(() -> verifier.verify("v1.3", json, hash)).doesNotThrowAnyException();
    }

    @Test
    void tamperedHashFailsAndNamesTheVersion() {
        String json = canonicalJson.write(RuleConfigs.v1_3());
        assertThatThrownBy(() -> verifier.verify("v1.3", json, "0".repeat(64)))
                .isInstanceOf(IllegalStateException.class)
                .satisfies(ex -> assertThat(ex.getMessage()).contains("v1.3"));
    }
}
