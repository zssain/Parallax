package com.parallax.application.shadow;

import com.fasterxml.jackson.databind.JsonNode;
import com.parallax.application.lab.AbstractLabIT;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Shadow mode (SPEC §10): a REPLAYED candidate (cutoff 700) scores live traffic silently. An 830 agrees
 * with the live v1.3; a 690 approval disagrees (REFER in the shadow). A forced shadow failure is
 * swallowed and leaves the live 201 and the shadow table unchanged.
 */
class ShadowIT extends AbstractLabIT {

    /** A mock so a single submission can force the shadow insert to fail. */
    @MockitoBean
    ShadowFaultHook shadowFaultHook;

    @Test
    void shadowScoresLiveTrafficAndSwallowsFailures() throws Exception {
        insertCandidate("v1.4", withApproveCutoff(700));
        pollDone(startReplay(STRATEGIST, "v1.4"));
        postJsonAs(STRATEGIST, "/api/v1/lab/versions/v1.4/shadow", "{\"enabled\":true}")
                .andExpect(status().isOk());

        // Ishaan scores 830 → APPROVED under both v1.3 and v1.4 → agrees.
        stubBureau("912345678", primeResponse("BP-ISHAAN", "48 Elm Street, Columbus OH"));
        JsonNode approved = read(submit(STRATEGIST, newKey(), defaultRequest())
                .andExpect(status().isCreated()).andReturn());
        assertThat(approved.get("ruleVersion").asText()).isEqualTo("v1.3");
        assertThat(approved.get("outcome").asText()).isEqualTo("APPROVED");

        // A 690 approves under v1.3 (cutoff 680) but refers under v1.4 (cutoff 700) → disagrees.
        stubBureau("915000690", response("BP-690", "PRIME", "500 Shadow Ave, Columbus OH",
                3, 1, 0, "0.400", 12, 1992, false));
        JsonNode disagree = read(submit(STRATEGIST, newKey(), scoreRequest())
                .andExpect(status().isCreated()).andReturn());
        assertThat(disagree.get("outcome").asText()).isEqualTo("APPROVED");
        assertThat(disagree.get("score").asInt()).isEqualTo(690);
        String disagreeId = disagree.get("applicationId").asText();

        JsonNode results = read(getAs(STRATEGIST, "/api/v1/lab/versions/v1.4/shadow-results").andReturn());
        assertThat(results.get("count").asLong()).isEqualTo(2);
        assertThat(results.get("disagreements").asLong()).isEqualTo(1);

        // The decision detail carries the shadow block for the disagreeing application.
        JsonNode detail = read(getAs(STRATEGIST, "/api/v1/applications/" + disagreeId).andReturn());
        JsonNode shadow = detail.get("shadow");
        assertThat(shadow.get("version").asText()).isEqualTo("v1.4");
        assertThat(shadow.get("outcome").asText()).isEqualTo("REFER");
        assertThat(shadow.get("agrees").asBoolean()).isFalse();

        // A forced shadow failure is swallowed: the live decision still returns 201 and no result is stored.
        Mockito.doThrow(new RuntimeException("forced shadow failure"))
                .when(shadowFaultHook).beforeShadowInsert();
        stubBureau("912345679", primeResponse("BP-ISHAAN2", "48 Elm Street, Columbus OH"));
        Map<String, Object> another = defaultRequest();
        another.put("ssn", "912345679");
        submit(STRATEGIST, newKey(), another).andExpect(status().isCreated());

        JsonNode after = read(getAs(STRATEGIST, "/api/v1/lab/versions/v1.4/shadow-results").andReturn());
        assertThat(after.get("count").asLong()).isEqualTo(2); // the failed evaluation stored nothing
    }

    /** A request that scores exactly 690 under v1.3 with the crafted bureau attributes above. */
    private Map<String, Object> scoreRequest() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("firstName", "Score");
        body.put("lastName", "Sixninety");
        body.put("dateOfBirth", "1991-06-15");
        body.put("ssn", "915000690");
        body.put("address", "500 Shadow Ave, Columbus OH");
        body.put("annualIncome", 30000);
        body.put("monthlyHousing", 600);
        body.put("monthlyDebt", 100);
        body.put("independentIncome", true);
        body.put("bureauConsent", true);
        body.put("product", "REWARDS_CARD");
        return body;
    }
}
