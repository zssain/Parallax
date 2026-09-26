package com.parallax.application.review;

import com.fasterxml.jackson.databind.JsonNode;
import com.parallax.application.intake.AbstractIntakeIT;
import com.parallax.application.jobs.RedecisionJob;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** reasonKind derivation (fraud, bureau) and that an override removes a B01 app from re-decision (SPEC §15). */
class ReviewReasonKindIT extends AbstractIntakeIT {

    private static final String UW = "priya.menon@parallax.dev";

    @Autowired
    RedecisionJob redecisionJob;

    @Test
    void addressMismatchIsAFraudReasonKind() throws Exception {
        String ssn = "937123456"; // NEAR_PRIME / ADDRESS_MISMATCH (SPEC §8) → F01
        stubBureau(ssn, referenceResponse("BP-F", ssn, "48 Elm Street, Columbus OH", 1996));
        Map<String, Object> body = defaultRequest();
        body.put("ssn", ssn);
        JsonNode response = read(submit(UW, newKey(), body).andExpect(status().isCreated()).andReturn());
        assertThat(response.get("outcome").asText()).isEqualTo("REFER");

        JsonNode item = queueItem(response.get("applicationId").asText());
        assertThat(item.get("reasonKind").asText()).isEqualTo("fraud");
        assertThat(item.get("fraudFlags").toString()).contains("F01");
    }

    @Test
    void bureauOutageIsABureauReasonKindAndOverrideStopsRedecision() throws Exception {
        stubBureauServerError();
        String ssn = "912345678";
        Map<String, Object> body = defaultRequest();
        body.put("ssn", ssn);
        JsonNode response = read(submit(UW, newKey(), body).andExpect(status().isCreated()).andReturn());
        assertThat(response.get("status").asText()).isEqualTo("BUREAU_UNAVAILABLE");
        String appId = response.get("applicationId").asText();

        JsonNode item = queueItem(appId);
        assertThat(item.get("reasonKind").asText()).isEqualTo("bureau");
        assertThat(item.get("reasonCodes").toString()).contains("B01");

        // Underwriter approves the stranded application.
        postJsonAs(UW, "/api/v1/reviews/" + appId, json(Map.of("decision", "APPROVED",
                "creditLimit", 1000, "overrideCode", "O4", "note", "Bureau data corrected manually")))
                .andExpect(status().isCreated());

        // Bureau recovers, but the re-decision job must skip the now-reviewed application.
        BUREAU.resetAll();
        stubBureau(ssn, referenceResponse("BP-BACK", ssn, "48 Elm Street, Columbus OH", 1996));
        bureauCircuit.close();
        assertThat(redecisionJob.runOnce()).doesNotContain(appId);

        String appStatus = jdbc.queryForObject(
                "SELECT status FROM application WHERE public_id = ?", String.class, appId);
        assertThat(appStatus).isEqualTo("REVIEWED");
    }

    private JsonNode queueItem(String appId) throws Exception {
        JsonNode queue = read(getAs(UW, "/api/v1/reviews/queue").andReturn());
        for (JsonNode item : queue) {
            if (item.get("applicationId").asText().equals(appId)) {
                return item;
            }
        }
        throw new AssertionError("application " + appId + " not in the review queue");
    }
}
