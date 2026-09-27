package com.parallax.application.internal;

import com.parallax.application.AbstractPostgresIT;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The internal rule-config endpoint is gated by the X-Internal-Token filter, not HTTP Basic (SPEC §15).
 * The correct token returns the LIVE config; a wrong or missing token is 401.
 */
@AutoConfigureMockMvc
class InternalRuleConfigIT extends AbstractPostgresIT {

    @Autowired
    MockMvc mvc;

    @Test
    void correctTokenReturnsTheLiveConfig() throws Exception {
        mvc.perform(get("/internal/v1/rule-config/live").header("X-Internal-Token", "internal-dev"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value("v1.3"))
                .andExpect(jsonPath("$.config.approveCutoff").value(680))
                .andExpect(jsonPath("$.config.bandLimits").isArray());
    }

    @Test
    void wrongTokenIsUnauthorized() throws Exception {
        mvc.perform(get("/internal/v1/rule-config/live").header("X-Internal-Token", "nope"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void missingTokenIsUnauthorized() throws Exception {
        mvc.perform(get("/internal/v1/rule-config/live"))
                .andExpect(status().isUnauthorized());
    }
}
