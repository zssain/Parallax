package com.parallax.application.lab;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** ASSISTANT never starts a replay: 404 when none exists, else 202 with the existing job and no new row. */
class ReplayAssistantIT extends AbstractLabIT {

    private static final String ASSISTANT = "assistant@parallax.dev";

    @Test
    void assistantResolvesExistingReplayOnly() throws Exception {
        seedHistory();
        insertCandidate("v1.4", withApproveCutoff(700));

        // No DONE replay yet → assistant gets 404.
        assistantReplay("v1.4").andExpect(status().isNotFound());

        // A strategist runs one to completion.
        String jobId = startReplay(STRATEGIST, "v1.4");
        pollDone(jobId);
        long jobsBefore = jdbc.queryForObject("SELECT count(*) FROM replay_job", Long.class);

        // Assistant now gets 202 with that same job and starts no new work.
        JsonNode body = read(assistantReplay("v1.4").andExpect(status().isAccepted()).andReturn());
        assertThat(body.get("jobId").asText()).isEqualTo(jobId);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM replay_job", Long.class)).isEqualTo(jobsBefore);
    }

    private org.springframework.test.web.servlet.ResultActions assistantReplay(String version) throws Exception {
        return mvc.perform(post("/api/v1/lab/versions/" + version + "/replays")
                .with(httpBasic(ASSISTANT, "assistant-dev"))
                .contentType(MediaType.APPLICATION_JSON).content("{}"));
    }
}
