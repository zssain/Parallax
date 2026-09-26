package com.parallax.application.review;

import com.fasterxml.jackson.databind.JsonNode;
import com.parallax.application.intake.AbstractIntakeIT;
import com.parallax.application.jobs.EngineRetryJob;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** An ENGINE_FAILED_MANUAL application (no base row) can be reviewed; the OVERRIDE stands alone (SPEC §6). */
class EngineFailedManualReviewIT extends AbstractIntakeIT {

    private static final String UW = "priya.menon@parallax.dev";

    @Autowired
    EngineRetryJob engineRetryJob;

    @Test
    void engineFailedManualApplicationIsDeclined() throws Exception {
        stubBureau("912345678", primeResponse("BP-FAIL", "48 Elm Street, Columbus OH"));
        stubDecisionEngineDown();

        JsonNode body = read(submit(UW, newKey(), defaultRequest())
                .andExpect(status().isAccepted()).andReturn());
        String appId = body.get("applicationId").asText();
        for (int i = 0; i < 3; i++) {
            engineRetryJob.runOnce();
        }
        assertThat(jdbc.queryForObject("SELECT status FROM application WHERE public_id = ?", String.class, appId))
                .isEqualTo("ENGINE_FAILED_MANUAL");

        // The reviewer declines with a fraud-confirmed override code.
        JsonNode result = read(postJsonAs(UW, "/api/v1/reviews/" + appId, json(Map.of(
                "decision", "DECLINED", "overrideCode", "O3", "note", "Confirmed fraud with the issuer")))
                .andExpect(status().isCreated()).andReturn());
        assertThat(result.get("outcome").asText()).isEqualTo("DECLINED");
        assertThat(result.get("creditLimit").asInt()).isZero();

        // The OVERRIDE row has no linked base and the application is REVIEWED.
        Long appDbId = jdbc.queryForObject("SELECT id FROM application WHERE public_id = ?", Long.class, appId);
        Map<String, Object> row = jdbc.queryForMap(
                "SELECT kind, outcome, score, linked_seq FROM decision_ledger WHERE application_id = ?", appDbId);
        assertThat(row.get("kind")).isEqualTo("OVERRIDE");
        assertThat(row.get("outcome")).isEqualTo("DECLINED");
        assertThat(row.get("score")).isNull();
        assertThat(row.get("linked_seq")).isNull();
        assertThat(jdbc.queryForObject("SELECT status FROM application WHERE public_id = ?", String.class, appId))
                .isEqualTo("REVIEWED");

        // Detail: base null, current is the override.
        JsonNode detail = read(getAs(UW, "/api/v1/applications/" + appId).andReturn());
        assertThat(detail.get("base").isNull()).isTrue();
        assertThat(detail.get("current").get("kind").asText()).isEqualTo("OVERRIDE");
        assertThat(detail.get("current").get("override").get("code").asText()).isEqualTo("O3");

        assertThat(read(getAs(UW, "/api/v1/ledger/verify").andReturn()).get("ok").asBoolean()).isTrue();
    }
}
