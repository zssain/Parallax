package com.parallax.application.review;

import com.fasterxml.jackson.databind.JsonNode;
import com.parallax.application.intake.AbstractIntakeIT;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** The 422/409/403 rules guarding an override (SPEC §15). */
class ReviewRulesIT extends AbstractIntakeIT {

    private static final String UW = "priya.menon@parallax.dev";
    private static final String STRATEGIST = "aditi.rao@parallax.dev";
    private static final String GOOD_NOTE = "Verified employment and income documents";

    @Test
    void shortNoteIsRejected() throws Exception {
        String appId = referApp();
        JsonNode problem = read(postJsonAs(UW, "/api/v1/reviews/" + appId,
                review("APPROVED", 1500, "O2", "ok")).andExpect(status().isUnprocessableEntity()).andReturn());
        assertThat(problem.get("detail").asText()).contains("at least 10 characters");
    }

    @Test
    void limitBelowFloorIsRejected() throws Exception {
        String appId = referApp();
        JsonNode problem = read(postJsonAs(UW, "/api/v1/reviews/" + appId,
                review("APPROVED", 200, "O2", GOOD_NOTE)).andExpect(status().isUnprocessableEntity()).andReturn());
        assertThat(problem.get("detail").asText()).isEqualTo("Limit must be at least $300");
    }

    @Test
    void limitAboveAtpMaxIsRejectedNamingTheMax() throws Exception {
        String appId = referApp();
        JsonNode problem = read(postJsonAs(UW, "/api/v1/reviews/" + appId,
                review("APPROVED", 13000, "O2", GOOD_NOTE)).andExpect(status().isUnprocessableEntity()).andReturn());
        assertThat(problem.get("detail").asText())
                .isEqualTo("Limit exceeds the ability-to-pay maximum of $12,200");
    }

    @Test
    void reviewingAnApprovedApplicationConflicts() throws Exception {
        String appId = approvedApp();
        postJsonAs(UW, "/api/v1/reviews/" + appId, review("DECLINED", null, "O5", GOOD_NOTE))
                .andExpect(status().isConflict());
    }

    @Test
    void strategistCannotRecordAnOverride() throws Exception {
        String appId = referApp();
        postJsonAs(STRATEGIST, "/api/v1/reviews/" + appId, review("APPROVED", 1500, "O2", GOOD_NOTE))
                .andExpect(status().isForbidden());
    }

    @Test
    void reviewingTwiceConflictsOnTheSecond() throws Exception {
        String appId = referApp();
        postJsonAs(UW, "/api/v1/reviews/" + appId, review("APPROVED", 1500, "O2", GOOD_NOTE))
                .andExpect(status().isCreated());
        postJsonAs(UW, "/api/v1/reviews/" + appId, review("DECLINED", null, "O5", GOOD_NOTE))
                .andExpect(status().isConflict());
    }

    // --- helpers ----------------------------------------------------------------------------------

    private String referApp() throws Exception {
        String ssn = "931000003";
        stubBureau(ssn, referenceResponse("BP-NP", ssn, "48 Elm Street, Columbus OH", 1996));
        Map<String, Object> body = defaultRequest();
        body.put("ssn", ssn);
        body.put("annualIncome", 45000);
        body.put("monthlyHousing", 1200);
        body.put("monthlyDebt", 300);
        JsonNode response = read(submit(UW, newKey(), body).andExpect(status().isCreated()).andReturn());
        assertThat(response.get("outcome").asText()).isEqualTo("REFER");
        return response.get("applicationId").asText();
    }

    private String approvedApp() throws Exception {
        String ssn = "912345678";
        stubBureau(ssn, primeResponse("BP-PR", "48 Elm Street, Columbus OH"));
        JsonNode response = read(submit(UW, newKey(), defaultRequest())
                .andExpect(status().isCreated()).andReturn());
        assertThat(response.get("outcome").asText()).isEqualTo("APPROVED");
        return response.get("applicationId").asText();
    }

    private String review(String decision, Integer creditLimit, String code, String note) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("decision", decision);
        if (creditLimit != null) {
            body.put("creditLimit", creditLimit);
        }
        body.put("overrideCode", code);
        body.put("note", note);
        return json(body);
    }
}
