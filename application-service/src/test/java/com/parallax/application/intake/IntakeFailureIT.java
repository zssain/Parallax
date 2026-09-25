package com.parallax.application.intake;

import org.junit.jupiter.api.Test;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class IntakeFailureIT extends AbstractIntakeIT {

    private static final String USER = "priya.menon@parallax.dev";

    @MockitoBean
    IntakeFailurePoint failurePoint;

    @Test
    void aFailureAfterTheKeyInsertAbandonsTheKey() throws Exception {
        doThrow(new RuntimeException("boom")).when(failurePoint).afterKeyInsert();
        String key = newKey();

        submit(USER, key, defaultRequest()).andExpect(status().is5xxServerError());

        Integer keys = jdbc.queryForObject(
                "SELECT count(*) FROM idempotency_key WHERE idem_key = ?", Integer.class, key);
        assertThat(keys).isZero();
        assertThat(count("application")).isZero();
    }
}
