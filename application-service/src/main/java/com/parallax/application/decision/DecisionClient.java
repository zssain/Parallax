package com.parallax.application.decision;

import com.parallax.engine.api.EvaluateRequest;
import com.parallax.engine.api.EvaluateResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.HttpServerErrorException;

import java.time.Duration;

/**
 * Calls decision-service {@code /internal/v1/evaluate} (SPEC §15). Throws {@link
 * DecisionUnavailableException} on an I/O error or a 5xx so the orchestrator can degrade. 2 s connect
 * and read timeouts.
 */
@Component
public class DecisionClient {

    private static final Duration TIMEOUT = Duration.ofSeconds(2);

    private final RestClient restClient;

    public DecisionClient(RestClient.Builder builder,
                          @Value("${parallax.decision.url:http://localhost:8081}") String decisionUrl,
                          @Value("${parallax.internal-token:internal-dev}") String internalToken) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(TIMEOUT);
        factory.setReadTimeout(TIMEOUT);
        this.restClient = builder
                .baseUrl(decisionUrl)
                .defaultHeader("X-Internal-Token", internalToken)
                .requestFactory(factory)
                .build();
    }

    public EvaluateResponse evaluate(EvaluateRequest request) {
        try {
            return restClient.post()
                    .uri("/internal/v1/evaluate")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .body(EvaluateResponse.class);
        } catch (HttpServerErrorException | ResourceAccessException e) {
            throw new DecisionUnavailableException("decision-service unavailable", e);
        }
    }
}
