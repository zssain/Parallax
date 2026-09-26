package com.parallax.assistant;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** The assistant requires an INTERNAL user: no auth → 401, and an unknown/CLIENT user → 401 (SPEC §9, §12). */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = "spring.ai.anthropic.api-key=")
class SecurityIT {

    @Autowired
    MockMvc mvc;

    @Test
    void noAuthenticationIsUnauthorized() throws Exception {
        mvc.perform(get("/api/v1/assistant/status")).andExpect(status().isUnauthorized());
    }

    @Test
    void unknownUserIsUnauthorized() throws Exception {
        mvc.perform(get("/api/v1/assistant/status")
                        .with(httpBasic("client@parallax.dev", "demo-password")))
                .andExpect(status().isUnauthorized());
    }
}
