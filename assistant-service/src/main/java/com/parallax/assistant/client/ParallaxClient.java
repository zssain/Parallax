package com.parallax.assistant.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.parallax.assistant.config.AssistantProperties;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;

/**
 * Read-only HTTP access to application-service using the assistant's ASSISTANT credentials (SPEC §12).
 * Every method is a GET except {@link #lookupReplay(String)}, which POSTs to the replay endpoint that,
 * for the ASSISTANT role, only ever returns an existing job (Prompt 13). 3 s connect/read timeouts.
 */
@Component
public class ParallaxClient {

    private static final Duration TIMEOUT = Duration.ofSeconds(3);

    private final RestClient restClient;

    public ParallaxClient(RestClient.Builder builder, AssistantProperties properties) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(TIMEOUT);
        factory.setReadTimeout(TIMEOUT);
        String basic = Base64.getEncoder().encodeToString(
                (properties.getCredentials().getUsername() + ":" + properties.getCredentials().getPassword())
                        .getBytes(StandardCharsets.UTF_8));
        this.restClient = builder
                .requestFactory(factory)
                .baseUrl(properties.getApplication().getUrl())
                .defaultHeader("Authorization", "Basic " + basic)
                .build();
    }

    public JsonNode getApplication(String applicationId) {
        return get("/api/v1/applications/{id}", applicationId);
    }

    public JsonNode getOverrideStats() {
        return get("/api/v1/reviews/override-stats");
    }

    public JsonNode getVersions() {
        return get("/api/v1/lab/versions");
    }

    public JsonNode compareVersions(String a, String b) {
        return restClient.get()
                .uri(uriBuilder -> uriBuilder.path("/api/v1/lab/versions/compare")
                        .queryParam("a", a).queryParam("b", b).build())
                .retrieve().body(JsonNode.class);
    }

    public JsonNode getReplay(String jobId) {
        return get("/api/v1/lab/replays/{jobId}", jobId);
    }

    /** POST to the replays endpoint; as ASSISTANT this returns an existing job only, or 404 → null. */
    public JsonNode lookupReplay(String version) {
        try {
            return restClient.post().uri("/api/v1/lab/versions/{v}/replays", version)
                    .retrieve().body(JsonNode.class);
        } catch (org.springframework.web.client.HttpClientErrorException.NotFound e) {
            return null;
        }
    }

    /** The latest drift report, or null when none has been computed yet (404). */
    public JsonNode getDriftLatest() {
        try {
            return get("/api/v1/drift/latest");
        } catch (org.springframework.web.client.HttpClientErrorException.NotFound e) {
            return null;
        }
    }

    private JsonNode get(String uri, Object... vars) {
        return restClient.get().uri(uri, vars).retrieve().body(JsonNode.class);
    }
}
