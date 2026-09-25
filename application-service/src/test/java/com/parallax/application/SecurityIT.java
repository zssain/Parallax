package com.parallax.application;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class SecurityIT extends AbstractPostgresIT {

    @Autowired
    MockMvc mvc;

    @Test
    void meWithoutCredentialsIs401ProblemDetail() throws Exception {
        mvc.perform(get("/api/v1/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(401));
    }

    @Test
    void meWithValidCredentialsReturnsIdentity() throws Exception {
        mvc.perform(get("/api/v1/me").with(httpBasic("aditi.rao@parallax.dev", "demo-password")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("aditi.rao@parallax.dev"))
                .andExpect(jsonPath("$.displayName").value("Aditi Rao"))
                .andExpect(jsonPath("$.role").value("STRATEGIST"));
    }

    @Test
    void meWithWrongPasswordIs401() throws Exception {
        mvc.perform(get("/api/v1/me").with(httpBasic("aditi.rao@parallax.dev", "wrong")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void unknownPathIsNeverOk() throws Exception {
        // Authenticated but not permitted → 403 (never 200); unauthenticated would be 401.
        mvc.perform(get("/api/v1/nope").with(httpBasic("aditi.rao@parallax.dev", "demo-password")))
                .andExpect(status().isForbidden());
    }
}
