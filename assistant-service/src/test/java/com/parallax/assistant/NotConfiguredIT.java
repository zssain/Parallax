package com.parallax.assistant;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** With a blank API key the service starts, reports not-configured, and refuses chat with 503 (SPEC §12). */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = "spring.ai.anthropic.api-key=")
class NotConfiguredIT {

    private static final String USER = "aditi.rao@parallax.dev";
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Autowired
    MockMvc mvc;

    @Test
    void statusReportsNotConfigured() throws Exception {
        JsonNode body = objectMapper.readTree(mvc.perform(get("/api/v1/assistant/status")
                        .with(httpBasic(USER, "demo-password")))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());

        assertThat(body.get("configured").asBoolean()).isFalse();
        assertThat(body.get("provider").asText()).isEqualTo("anthropic");
    }

    @Test
    void chatReturns503WithExactTitle() throws Exception {
        JsonNode body = objectMapper.readTree(mvc.perform(post("/api/v1/assistant/chat")
                        .with(httpBasic(USER, "demo-password"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"Why was APP-1041 declined?\"}"))
                .andExpect(status().isServiceUnavailable()).andReturn().getResponse().getContentAsString());

        assertThat(body.get("title").asText()).isEqualTo("Assistant model not configured");
    }
}
