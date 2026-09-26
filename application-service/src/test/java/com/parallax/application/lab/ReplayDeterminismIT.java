package com.parallax.application.lab;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** Two replays of the same candidate produce an identical report (SPEC §10), ignoring timings. */
class ReplayDeterminismIT extends AbstractLabIT {

    @Test
    void sameCandidateReplaysIdentically() throws Exception {
        seedHistory();
        insertCandidate("v1.4", withApproveCutoff(700));

        JsonNode first = pollDone(startReplay(STRATEGIST, "v1.4")).get("report");
        JsonNode second = pollDone(startReplay(STRATEGIST, "v1.4")).get("report");

        // The report JSON carries no timings, so structural equality is a like-for-like comparison.
        assertThat(second).isEqualTo(first);
    }
}
