package com.parallax.application.lab;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** The flips page and flip detail return both decisions with correct cutoffs (SPEC §15). */
class FlipDetailIT extends AbstractLabIT {

    @Test
    void flipsPageAndDetailReturnBothDecisions() throws Exception {
        seedHistory();
        insertCandidate("v1.4", withApproveCutoff(700));
        String jobId = startReplay(STRATEGIST, "v1.4");
        pollDone(jobId);

        JsonNode page = read(getAs(STRATEGIST, "/api/v1/lab/replays/" + jobId + "/flips?size=10").andReturn());
        assertThat(page.get("items")).isNotEmpty();
        JsonNode first = page.get("items").get(0);
        long seq = first.get("seq").asLong();
        assertThat(first.get("baseline").get("outcome").asText()).isEqualTo("APPROVED");
        assertThat(first.get("candidateReasons")).isNotNull();

        JsonNode detail = read(getAs(STRATEGIST, "/api/v1/lab/replays/" + jobId + "/flips/" + seq).andReturn());
        assertThat(detail.get("seq").asLong()).isEqualTo(seq);
        assertThat(detail.get("engineInput")).isNotNull();

        assertThat(detail.get("baseline").get("version").asText()).isEqualTo("v1.3");
        assertThat(detail.get("baseline").get("approveCutoff").asInt()).isEqualTo(680);
        assertThat(detail.get("candidate").get("version").asText()).isEqualTo("v1.4");
        assertThat(detail.get("candidate").get("approveCutoff").asInt()).isEqualTo(700);

        // Both full decisions are present, including scoreParts.
        assertThat(detail.get("baseline").get("decision").get("outcome").asText()).isEqualTo("APPROVED");
        assertThat(detail.get("candidate").get("decision").get("outcome").asText()).isIn("REFER", "DECLINED");
        assertThat(detail.get("baseline").get("decision").get("scoreParts")).isNotEmpty();
    }
}
