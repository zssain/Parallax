package com.parallax.application.intake;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class IntakeSecurityIT extends AbstractIntakeIT {

    @Test
    void withoutAuthenticationIsUnauthorized() throws Exception {
        mvc.perform(post("/api/v1/applications")
                        .header("Idempotency-Key", newKey())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(defaultRequest())))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void auditorMayNotSubmitApplications() throws Exception {
        // AUDITOR is not one of CLIENT/UNDERWRITER/STRATEGIST/APPROVER.
        submit("sam.iyer@parallax.dev", newKey(), defaultRequest())
                .andExpect(status().isForbidden());
    }
}
