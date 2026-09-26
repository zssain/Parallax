package com.parallax.application.lab;

import com.fasterxml.jackson.databind.JsonNode;
import com.parallax.engine.config.RuleConfigs;
import com.parallax.engine.model.RuleConfig;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Editing rules: immutability, config validation, the one-open-candidate rule and REPLAYED → DRAFT (SPEC §10). */
class EditRulesIT extends AbstractLabIT {

    @Test
    void secondCandidateWhileOneIsOpenIsConflict() throws Exception {
        postJsonAs(STRATEGIST, "/api/v1/lab/versions", "{}").andExpect(status().isCreated());

        JsonNode conflict = read(postJsonAs(STRATEGIST, "/api/v1/lab/versions", "{}")
                .andExpect(status().isConflict()).andReturn());
        assertThat(conflict.get("detail").asText())
                .isEqualTo("Finish or discard the open candidate first — one candidate at a time.");
    }

    @Test
    void invalidConfigIsUnprocessableWithCutoffsError() throws Exception {
        JsonNode created = read(postJsonAs(STRATEGIST, "/api/v1/lab/versions", "{}")
                .andExpect(status().isCreated()).andReturn());
        String version = created.get("version").asText();

        // referCutoff (700) is not below approveCutoff (680): "Cutoffs out of order…".
        RuleConfig invalid = withCutoffs(680, 700);
        JsonNode body = read(putJsonAs(STRATEGIST, "/api/v1/lab/versions/" + version + "/config",
                canonicalJson.write(invalid)).andExpect(status().isUnprocessableEntity()).andReturn());

        assertThat(body.get("errors")).isNotNull();
        boolean listsCutoffs = false;
        for (JsonNode e : body.get("errors")) {
            if (e.asText().contains("Cutoffs out of order")) {
                listsCutoffs = true;
            }
        }
        assertThat(listsCutoffs).isTrue();
    }

    @Test
    void editingUsedVersionIsImmutable() throws Exception {
        // One live decision stamps v1.3.first_used_at, making it immutable.
        stubBureau("912345678", primeResponse("BP-EDIT", "48 Elm Street, Columbus OH"));
        submit(STRATEGIST, newKey(), defaultRequest()).andExpect(status().isCreated());

        JsonNode body = read(putJsonAs(STRATEGIST, "/api/v1/lab/versions/v1.3/config",
                canonicalJson.write(withApproveCutoff(700))).andExpect(status().isConflict()).andReturn());
        assertThat(body.get("detail").asText()).contains("immutable");
    }

    @Test
    void editingReplayedReturnsToDraftAndProposeNeedsAnotherReplay() throws Exception {
        JsonNode created = read(postJsonAs(STRATEGIST, "/api/v1/lab/versions",
                createBody(withApproveCutoff(700))).andExpect(status().isCreated()).andReturn());
        String version = created.get("version").asText();

        pollDone(startReplay(STRATEGIST, version));
        assertThat(versionStatus(version)).isEqualTo("REPLAYED");

        // Editing the config returns it to DRAFT and detaches its replay.
        putJsonAs(STRATEGIST, "/api/v1/lab/versions/" + version + "/config",
                canonicalJson.write(withApproveCutoff(710))).andExpect(status().isOk());
        assertThat(versionStatus(version)).isEqualTo("DRAFT");

        // Propose is blocked until the current config is replayed again.
        JsonNode blocked = read(postAs(STRATEGIST, "/api/v1/lab/versions/" + version + "/propose")
                .andExpect(status().isConflict()).andReturn());
        assertThat(blocked.get("detail").asText()).isEqualTo("Replay the current configuration before proposing");

        pollDone(startReplay(STRATEGIST, version));
        postAs(STRATEGIST, "/api/v1/lab/versions/" + version + "/propose").andExpect(status().isOk());
        assertThat(versionStatus(version)).isEqualTo("PROPOSED");
    }

    private String versionStatus(String version) {
        return jdbc.queryForObject("SELECT status FROM rule_version WHERE version = ?", String.class, version);
    }

    private static RuleConfig withCutoffs(int approveCutoff, int referCutoff) {
        RuleConfig v = RuleConfigs.v1_3();
        return new RuleConfig(approveCutoff, referCutoff, v.minPayPct(), v.atpShare(), v.livingCost(),
                v.minLimit(), v.bandLimits(), v.utilPts(), v.inqPts(), v.delqPts(), v.tradelinePts(),
                v.fileAgePts(), v.incomePts(), v.ccf(), v.lgd());
    }
}
