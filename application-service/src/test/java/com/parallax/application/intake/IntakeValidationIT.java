package com.parallax.application.intake;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class IntakeValidationIT extends AbstractIntakeIT {

    private static final String USER = "priya.menon@parallax.dev";

    @Test
    void missingIdempotencyKeyIsRejected() throws Exception {
        submit(USER, null, defaultRequest())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[*].field", hasItem("Idempotency-Key")));
    }

    @Test
    void ssnNotStartingWithNineIsRejected() throws Exception {
        Map<String, Object> body = defaultRequest();
        body.put("ssn", "812345678");
        submit(USER, newKey(), body)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[*].field", hasItem("ssn")));
    }

    @Test
    void tooShortPhoneIsRejected() throws Exception {
        Map<String, Object> body = defaultRequest();
        body.put("phone", "12");
        submit(USER, newKey(), body)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[*].field", hasItem("phone")));
    }
}
