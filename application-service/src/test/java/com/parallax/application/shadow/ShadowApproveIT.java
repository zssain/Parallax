package com.parallax.application.shadow;

import com.parallax.application.lab.AbstractLabIT;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Approving the version that is running in shadow mode clears the shadow slot (SPEC §10). */
class ShadowApproveIT extends AbstractLabIT {

    private static final String VIKRAM = "vikram.nair@parallax.dev";

    @Test
    void approvingTheShadowVersionClearsShadowConfig() throws Exception {
        insertCandidate("v1.4", withApproveCutoff(700));
        pollDone(startReplay(STRATEGIST, "v1.4"));

        postJsonAs(STRATEGIST, "/api/v1/lab/versions/v1.4/shadow", "{\"enabled\":true}")
                .andExpect(status().isOk());
        assertThat(shadowVersion()).isEqualTo("v1.4");

        postAs(STRATEGIST, "/api/v1/lab/versions/v1.4/propose").andExpect(status().isOk());
        postAs(VIKRAM, "/api/v1/lab/versions/v1.4/approve").andExpect(status().isOk());

        assertThat(shadowVersion()).isNull();
    }

    private String shadowVersion() {
        return jdbc.queryForObject("SELECT version FROM shadow_config WHERE id = 1", String.class);
    }
}
