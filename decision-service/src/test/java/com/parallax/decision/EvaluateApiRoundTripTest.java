package com.parallax.decision;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.parallax.engine.api.EvaluateRequest;
import com.parallax.engine.api.EvaluateResponse;
import com.parallax.engine.config.RuleConfigs;
import com.parallax.engine.model.Decision;
import com.parallax.engine.model.EngineInput;
import com.parallax.engine.model.PullType;
import com.parallax.engine.scoring.DecisionEngine;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Proves the shared plain-record contracts bind with Jackson (records need {@code -parameters},
 * which spring-boot-starter-parent enables). A round-trip must reconstruct an equal object.
 */
class EvaluateApiRoundTripTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final EngineInput ISHAAN = new EngineInput(
            30, 1996, 64000, 1350, 280, 0.08, 0, 0, 12, 156,
            true, true, false, 1997, false, 1, PullType.HARD);

    @Test
    void evaluateRequestRoundTrips() throws Exception {
        EvaluateRequest request = new EvaluateRequest("v1.3", RuleConfigs.v1_3(), ISHAAN);
        String json = objectMapper.writeValueAsString(request);
        assertThat(objectMapper.readValue(json, EvaluateRequest.class)).isEqualTo(request);
    }

    @Test
    void evaluateResponseRoundTrips() throws Exception {
        Decision decision = DecisionEngine.evaluate(ISHAAN, RuleConfigs.v1_3());
        EvaluateResponse response = new EvaluateResponse("v1.3", "engine-1.0.0", "sc-2.1", decision);
        String json = objectMapper.writeValueAsString(response);
        assertThat(objectMapper.readValue(json, EvaluateResponse.class)).isEqualTo(response);
    }
}
