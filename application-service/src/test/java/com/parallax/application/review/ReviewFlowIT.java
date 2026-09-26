package com.parallax.application.review;

import com.fasterxml.jackson.databind.JsonNode;
import com.parallax.application.intake.AbstractIntakeIT;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Happy path: a score-band REFER surfaces in the queue and an underwriter approves it (SPEC §15). */
class ReviewFlowIT extends AbstractIntakeIT {

    private static final String UW = "priya.menon@parallax.dev";

    @Test
    void referAppearsInQueueAndIsApprovedByOverride() throws Exception {
        String appId = submitNearPrimeRefer("931000003");

        // The queue lists it with reasonKind score and a suggested limit capped at 2000.
        JsonNode queue = read(getAs(UW, "/api/v1/reviews/queue").andExpect(status().isOk()).andReturn());
        JsonNode item = findInQueue(queue, appId);
        assertThat(item.get("reasonKind").asText()).isEqualTo("score");
        assertThat(item.get("score").asInt()).isEqualTo(655);
        assertThat(item.get("atpMax").asInt()).isEqualTo(12200);
        assertThat(item.get("suggestedLimit").asInt()).isEqualTo(2000);
        long baseSeq = item.get("baseSeq").asLong();

        // Approve with a valid limit, override code and note → 201.
        String body = json(Map.of("decision", "APPROVED", "creditLimit", 1500,
                "overrideCode", "O2", "note", "Income verified via paystubs"));
        JsonNode result = read(postJsonAs(UW, "/api/v1/reviews/" + appId, body)
                .andExpect(status().isCreated()).andReturn());
        assertThat(result.get("outcome").asText()).isEqualTo("APPROVED");
        assertThat(result.get("creditLimit").asInt()).isEqualTo(1500);

        // The ledger has an OVERRIDE row linked to the base; the original REFER row is untouched.
        Long linked = jdbc.queryForObject(
                "SELECT linked_seq FROM decision_ledger WHERE kind = 'OVERRIDE' AND application_id ="
                        + " (SELECT id FROM application WHERE public_id = ?)", Long.class, appId);
        assertThat(linked).isEqualTo(baseSeq);

        String appStatus = jdbc.queryForObject(
                "SELECT status FROM application WHERE public_id = ?", String.class, appId);
        assertThat(appStatus).isEqualTo("REVIEWED");

        // The queue no longer lists it.
        JsonNode queueAfter = read(getAs(UW, "/api/v1/reviews/queue").andReturn());
        assertThat(findInQueueOrNull(queueAfter, appId)).isNull();

        // Detail shows the override block; the chain still verifies.
        JsonNode detail = read(getAs(UW, "/api/v1/applications/" + appId).andReturn());
        assertThat(detail.get("current").get("kind").asText()).isEqualTo("OVERRIDE");
        JsonNode override = detail.get("current").get("override");
        assertThat(override.get("by").asText()).isEqualTo("Priya Menon");
        assertThat(override.get("code").asText()).isEqualTo("O2");
        assertThat(override.get("codeDescription").asText()).isEqualTo("Income verified");
        assertThat(override.get("note").asText()).isEqualTo("Income verified via paystubs");

        JsonNode verify = read(getAs(UW, "/api/v1/ledger/verify").andReturn());
        assertThat(verify.get("ok").asBoolean()).isTrue();
    }

    /** Submit a near-prime applicant (SPEC §4 example) → REFER 655; returns the public id. */
    private String submitNearPrimeRefer(String ssn) throws Exception {
        stubBureau(ssn, referenceResponse("BP-" + ssn, ssn, "48 Elm Street, Columbus OH", 1996));
        Map<String, Object> body = defaultRequest();
        body.put("firstName", "Sara");
        body.put("lastName", "Khan");
        body.put("ssn", ssn);
        body.put("annualIncome", 45000);
        body.put("monthlyHousing", 1200);
        body.put("monthlyDebt", 300);
        JsonNode response = read(submit(UW, newKey(), body).andExpect(status().isCreated()).andReturn());
        assertThat(response.get("outcome").asText()).isEqualTo("REFER");
        assertThat(response.get("score").asInt()).isEqualTo(655);
        return response.get("applicationId").asText();
    }

    private JsonNode findInQueue(JsonNode queue, String appId) {
        JsonNode item = findInQueueOrNull(queue, appId);
        assertThat(item).as("application %s in the review queue", appId).isNotNull();
        return item;
    }

    private JsonNode findInQueueOrNull(JsonNode queue, String appId) {
        for (JsonNode item : queue) {
            if (item.get("applicationId").asText().equals(appId)) {
                return item;
            }
        }
        return null;
    }
}
