package com.parallax.application.resilience;

import com.fasterxml.jackson.databind.JsonNode;
import com.parallax.application.intake.AbstractIntakeIT;
import com.parallax.application.jobs.EngineRetryJob;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class EnginePendingIT extends AbstractIntakeIT {

    private static final String USER = "priya.menon@parallax.dev";

    @Autowired
    EngineRetryJob engineRetryJob;

    @Test
    void engineOutageQueuesThenTheRetryJobFinishesIt() throws Exception {
        stubBureau("912345678", primeResponse("BP-EP", "48 Elm Street, Columbus OH"));
        stubDecisionEngineDown();

        String key = newKey();
        JsonNode body = read(submit(USER, key, defaultRequest()).andExpect(status().isAccepted()).andReturn());
        assertThat(body.get("status").asText()).isEqualTo("ENGINE_PENDING");
        String applicationId = body.get("applicationId").asText();

        // Replaying the same key returns the same 202.
        submit(USER, key, defaultRequest())
                .andExpect(status().isAccepted())
                .andExpect(header().string("Idempotent-Replay", "true"));

        JsonNode pending = read(getAs(USER, "/api/v1/applications/" + applicationId).andReturn());
        assertThat(pending.get("status").asText()).isEqualTo("ENGINE_PENDING");
        assertThat(pending.get("current").isNull()).isTrue();
        assertThat(pending.get("trail")).isEmpty();

        // The engine heals; the retry job decides the application.
        stubDecisionEngineInProcess();
        List<String> decided = engineRetryJob.runOnce();
        assertThat(decided).contains(applicationId);

        JsonNode decidedDetail = read(getAs(USER, "/api/v1/applications/" + applicationId).andReturn());
        assertThat(decidedDetail.get("status").asText()).isEqualTo("DECIDED");
        assertThat(decidedDetail.get("current").get("outcome").asText()).isNotEmpty();
    }
}
