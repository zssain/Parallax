package com.parallax.application.resilience;

import com.fasterxml.jackson.databind.JsonNode;
import com.parallax.application.intake.AbstractIntakeIT;
import com.parallax.application.jobs.EngineRetryJob;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class EngineFailedManualIT extends AbstractIntakeIT {

    private static final String USER = "priya.menon@parallax.dev";

    @Autowired
    EngineRetryJob engineRetryJob;

    @Test
    void repeatedEngineFailuresEndInManualReview() throws Exception {
        stubBureau("912345678", primeResponse("BP-FAIL", "48 Elm Street, Columbus OH"));
        stubDecisionEngineDown();

        JsonNode body = read(submit(USER, newKey(), defaultRequest()).andExpect(status().isAccepted()).andReturn());
        String applicationId = body.get("applicationId").asText();

        // Engine stays down: run the retry job until the attempts are exhausted.
        for (int i = 0; i < 3; i++) {
            engineRetryJob.runOnce();
        }

        String finalStatus = jdbc.queryForObject(
                "SELECT status FROM application WHERE public_id = ?", String.class, applicationId);
        assertThat(finalStatus).isEqualTo("ENGINE_FAILED_MANUAL");
    }
}
