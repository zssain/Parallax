package com.parallax.account.cli;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.parallax.engine.model.RuleConfig;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Duration;

/**
 * Fetches the LIVE rule config from application-service {@code GET /internal/v1/rule-config/live} with the
 * shared internal token (SPEC §15), and caches it for five minutes. account-service uses only the
 * affordability fields (atpShare, minPayPct, livingCost) to size credit-line increases (SPEC §13).
 */
@Component
public class RuleConfigClient {

    private static final long TTL_MILLIS = Duration.ofMinutes(5).toMillis();
    private static final Duration TIMEOUT = Duration.ofSeconds(2);

    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    private volatile RuleConfig cached;
    private volatile long fetchedAtMillis;

    public RuleConfigClient(RestClient.Builder builder, ObjectMapper objectMapper,
                            @Value("${parallax.application.url:http://localhost:8080}") String applicationUrl,
                            @Value("${parallax.internal-token:internal-dev}") String internalToken) {
        this.objectMapper = objectMapper;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(TIMEOUT);
        factory.setReadTimeout(TIMEOUT);
        this.restClient = builder
                .baseUrl(applicationUrl)
                .defaultHeader("X-Internal-Token", internalToken)
                .requestFactory(factory)
                .build();
    }

    public RuleConfig liveConfig() {
        long now = System.currentTimeMillis();
        RuleConfig current = cached;
        if (current != null && now - fetchedAtMillis < TTL_MILLIS) {
            return current;
        }
        JsonNode body = restClient.get().uri("/internal/v1/rule-config/live").retrieve().body(JsonNode.class);
        if (body == null || body.get("config") == null) {
            throw new IllegalStateException("rule-config/live returned no config");
        }
        RuleConfig fresh = objectMapper.convertValue(body.get("config"), RuleConfig.class);
        cached = fresh;
        fetchedAtMillis = now;
        return fresh;
    }
}
