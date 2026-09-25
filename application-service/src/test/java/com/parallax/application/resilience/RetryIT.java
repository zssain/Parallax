package com.parallax.application.resilience;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.parallax.application.AbstractPostgresIT;
import com.parallax.application.decision.DecisionClient;
import com.parallax.application.decision.DecisionUnavailableException;
import com.parallax.engine.api.EvaluateRequest;
import com.parallax.engine.config.RuleConfigs;
import com.parallax.engine.model.EngineInput;
import com.parallax.engine.model.PullType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Exercises the real DecisionClient's @Retry (this test does not mock it): a failing endpoint is
 * retried three times with increasing backoff (SPEC §3, Prompt 10).
 */
class RetryIT extends AbstractPostgresIT {

    private static final EngineInput ISHAAN = new EngineInput(
            30, 1996, 64000, 1350, 280, 0.08, 0, 0, 12, 156,
            true, true, false, 1997, false, 1, PullType.HARD);

    static final WireMockServer DECISION = new WireMockServer(options().dynamicPort());

    static {
        DECISION.start();
    }

    @Autowired
    DecisionClient decisionClient;

    @DynamicPropertySource
    static void decisionUrl(DynamicPropertyRegistry registry) {
        registry.add("parallax.decision.url", () -> "http://localhost:" + DECISION.port());
    }

    @BeforeEach
    void resetStub() {
        DECISION.resetAll();
    }

    @Test
    void theDecisionCallRetriesThreeTimesWithBackoff() {
        DECISION.stubFor(post(urlEqualTo("/internal/v1/evaluate")).willReturn(aResponse().withStatus(503)));
        EvaluateRequest request = new EvaluateRequest("v1.3", RuleConfigs.v1_3(), ISHAAN);

        long start = System.nanoTime();
        assertThatThrownBy(() -> decisionClient.evaluate(request))
                .isInstanceOf(DecisionUnavailableException.class);
        long elapsedMs = (System.nanoTime() - start) / 1_000_000;

        DECISION.verify(3, postRequestedFor(urlEqualTo("/internal/v1/evaluate")));
        assertThat(elapsedMs).isGreaterThanOrEqualTo(500); // ~200ms + ~400ms backoff between attempts
    }
}
