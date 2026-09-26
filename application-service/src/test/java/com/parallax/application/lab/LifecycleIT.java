package com.parallax.application.lab;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The full governance lifecycle (SPEC §10): draft → edit → replay → propose → approve (promote) →
 * decide under the new version → rollback, with the exact GOVERNANCE ledger notes and a verified chain.
 */
class LifecycleIT extends AbstractLabIT {

    private static final String VIKRAM = "vikram.nair@parallax.dev";

    @Test
    void draftReplayProposeApprovePromoteThenRollback() throws Exception {
        seedHistory();

        // Aditi drafts v1.4, edits it to a tighter cutoff (700) and replays it.
        JsonNode created = read(postJsonAs(STRATEGIST, "/api/v1/lab/versions", "{}")
                .andExpect(status().isCreated()).andReturn());
        String version = created.get("version").asText();
        assertThat(version).isEqualTo("v1.4");

        putJsonAs(STRATEGIST, "/api/v1/lab/versions/" + version + "/config",
                canonicalJson.write(withApproveCutoff(700))).andExpect(status().isOk());

        pollDone(startReplay(STRATEGIST, version));
        assertThat(versionStatus(version)).isEqualTo("REPLAYED");

        // Aditi proposes; Vikram approves. v1.4 → LIVE, v1.3 → RETIRED.
        postAs(STRATEGIST, "/api/v1/lab/versions/" + version + "/propose").andExpect(status().isOk());
        postAs(VIKRAM, "/api/v1/lab/versions/" + version + "/approve").andExpect(status().isOk());
        assertThat(versionStatus("v1.4")).isEqualTo("LIVE");
        assertThat(versionStatus("v1.3")).isEqualTo("RETIRED");

        JsonNode promote = governance("PROMOTE");
        assertThat(promote.get("note").asText())
                .isEqualTo("v1.4 promoted to LIVE · proposed by Aditi Rao · approved by Vikram Nair · replaced v1.3");
        assertThat(promote.get("replaced").asText()).isEqualTo("v1.3");

        // A new application is now decided under v1.4.
        stubBureau("912345678", primeResponse("BP-LIFE", "48 Elm Street, Columbus OH"));
        JsonNode decision = read(submit(STRATEGIST, newKey(), defaultRequest())
                .andExpect(status().isCreated()).andReturn());
        assertThat(decision.get("ruleVersion").asText()).isEqualTo("v1.4");

        // Vikram rolls back to v1.3.
        postAs(VIKRAM, "/api/v1/lab/rollback").andExpect(status().isOk());
        assertThat(versionStatus("v1.3")).isEqualTo("LIVE");
        assertThat(versionStatus("v1.4")).isEqualTo("RETIRED");

        JsonNode rollback = governance("ROLLBACK");
        assertThat(rollback.get("note").asText()).isEqualTo("Rollback: v1.4 → v1.3 by Vikram Nair");

        JsonNode verify = read(getAs(STRATEGIST, "/api/v1/ledger/verify").andReturn());
        assertThat(verify.get("ok").asBoolean()).isTrue();
    }

    private String versionStatus(String version) {
        return jdbc.queryForObject("SELECT status FROM rule_version WHERE version = ?", String.class, version);
    }

    private JsonNode governance(String action) throws Exception {
        String json = jdbc.queryForObject(
                "SELECT governance_detail::text FROM decision_ledger WHERE kind = 'GOVERNANCE'"
                        + " AND governance_detail->>'action' = ? ORDER BY seq DESC LIMIT 1",
                String.class, action);
        return objectMapper.readTree(json);
    }
}
