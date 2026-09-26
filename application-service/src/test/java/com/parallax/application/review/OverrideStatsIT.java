package com.parallax.application.review;

import com.fasterxml.jackson.databind.JsonNode;
import com.parallax.application.intake.AbstractIntakeIT;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Override rate by band over a crafted set (SPEC §15). */
class OverrideStatsIT extends AbstractIntakeIT {

    private static final String UW = "priya.menon@parallax.dev";

    @Test
    void bandsCountRefersAndOverridesExactly() throws Exception {
        // Two near-prime REFERs (score 655 → band 620–679); one bureau-outage REFER (no score).
        String a = nearPrimeRefer("931000003");
        nearPrimeRefer("931000004");
        postJsonAs(UW, "/api/v1/reviews/" + a, json(Map.of("decision", "APPROVED", "creditLimit", 1500,
                "overrideCode", "O2", "note", "Income verified via documents"))).andExpect(status().isCreated());

        BUREAU.resetAll();
        stubBureauServerError();
        Map<String, Object> b01 = defaultRequest();
        b01.put("ssn", "912345678");
        JsonNode outage = read(submit(UW, newKey(), b01).andExpect(status().isCreated()).andReturn());
        assertThat(outage.get("status").asText()).isEqualTo("BUREAU_UNAVAILABLE");

        JsonNode bands = read(getAs(UW, "/api/v1/reviews/override-stats").andExpect(status().isOk()).andReturn())
                .get("bands");
        assertThat(bands).hasSize(4);

        JsonNode lt620 = bands.get(0);
        JsonNode mid = bands.get(1);
        JsonNode hi = bands.get(2);
        JsonNode noScore = bands.get(3);

        assertThat(lt620.get("band").asText()).isEqualTo("<620");
        assertThat(lt620.get("refers").asInt()).isZero();

        assertThat(mid.get("band").asText()).contains("620");
        assertThat(mid.get("refers").asInt()).isEqualTo(2);
        assertThat(mid.get("overriddenToApprove").asInt()).isEqualTo(1);
        assertThat(mid.get("rate").asDouble()).isEqualTo(0.5);

        assertThat(hi.get("band").asText()).isEqualTo("680+");
        assertThat(hi.get("refers").asInt()).isZero();

        assertThat(noScore.get("band").asText()).isEqualTo("no score");
        assertThat(noScore.get("refers").asInt()).isEqualTo(1);
        assertThat(noScore.get("overriddenToApprove").asInt()).isZero();
        assertThat(noScore.get("rate").asDouble()).isZero();
    }

    private String nearPrimeRefer(String ssn) throws Exception {
        stubBureau(ssn, referenceResponse("BP-" + ssn, ssn, "48 Elm Street, Columbus OH", 1996));
        Map<String, Object> body = defaultRequest();
        body.put("ssn", ssn);
        body.put("annualIncome", 45000);
        body.put("monthlyHousing", 1200);
        body.put("monthlyDebt", 300);
        JsonNode response = read(submit(UW, newKey(), body).andExpect(status().isCreated()).andReturn());
        assertThat(response.get("outcome").asText()).isEqualTo("REFER");
        assertThat(response.get("score").asInt()).isEqualTo(655);
        return response.get("applicationId").asText();
    }
}
