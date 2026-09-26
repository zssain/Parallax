package com.parallax.application.lab;

import org.junit.jupiter.api.Test;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** A second replay while one is QUEUED/RUNNING is rejected with 409 (SPEC §10). */
class ReplayConcurrencyIT extends AbstractLabIT {

    @Test
    void secondReplayWhileOneRunsConflicts() throws Exception {
        insertCandidate("v1.5", withApproveCutoff(690));

        // Simulate a job already in flight (deterministic; avoids racing the fast in-memory replay).
        jdbc.update("INSERT INTO replay_job (id, candidate_version, baseline_version, candidate_config_hash,"
                + " status, progress, created_at) VALUES ('RJ-INFLGT', 'v1.4', 'v1.3', ?, 'RUNNING', 0, now())",
                "0".repeat(64));

        postJsonAs(STRATEGIST, "/api/v1/lab/versions/v1.5/replays", "{}")
                .andExpect(status().isConflict());
    }
}
