package com.parallax.application.lab;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Maker-checker: the proposer can never approve their own version; a different approver can (SPEC §10). */
class MakerCheckerIT extends AbstractLabIT {

    private static final String STRAT2 = "strat2@parallax.dev";   // STRATEGIST + APPROVER
    private static final String VIKRAM = "vikram.nair@parallax.dev";

    @Test
    void proposerCannotApproveButAnotherApproverCan() throws Exception {
        // strat2 authors, replays and proposes a candidate.
        JsonNode created = read(postJsonAs(STRAT2, "/api/v1/lab/versions", "{}")
                .andExpect(status().isCreated()).andReturn());
        String version = created.get("version").asText();

        pollDone(startReplay(STRAT2, version));
        postAs(STRAT2, "/api/v1/lab/versions/" + version + "/propose").andExpect(status().isOk());

        // strat2 also holds APPROVER, but is the proposer → 403 with the exact maker-checker message.
        JsonNode denied = read(postAs(STRAT2, "/api/v1/lab/versions/" + version + "/approve")
                .andExpect(status().isForbidden()).andReturn());
        assertThat(denied.get("detail").asText()).isEqualTo("Maker-checker: the proposer cannot approve");

        // The version is still PROPOSED — the failed approval changed nothing.
        assertThat(jdbc.queryForObject("SELECT status FROM rule_version WHERE version = ?", String.class, version))
                .isEqualTo("PROPOSED");

        // A different approver (Vikram) can promote it.
        postAs(VIKRAM, "/api/v1/lab/versions/" + version + "/approve").andExpect(status().isOk());
        assertThat(jdbc.queryForObject("SELECT status FROM rule_version WHERE version = ?", String.class, version))
                .isEqualTo("LIVE");
    }
}
