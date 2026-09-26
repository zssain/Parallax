package com.parallax.application.lab;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** A tighter candidate (approveCutoff 700) approves fewer, flips only APPROVED→REFER/DECLINED (SPEC §10). */
class ReplayTighteningIT extends AbstractLabIT {

    @Test
    void tighterCutoffApprovesFewerAndFlipsOnlyDownward() throws Exception {
        seedHistory();
        insertCandidate("v1.4", withApproveCutoff(700));

        String jobId = startReplay(STRATEGIST, "v1.4");
        JsonNode job = pollDone(jobId);
        JsonNode report = job.get("report");

        long n = report.get("n").asLong();
        long baselineApprovals = report.get("baseline").get("approvals").asLong();
        long candidateApprovals = report.get("candidate").get("approvals").asLong();
        assertThat(candidateApprovals).isLessThanOrEqualTo(baselineApprovals);

        // Tightening never newly approves anyone, so no candidate approval is "outcome unknown".
        assertThat(report.get("outcomeUnknown").get("count").asLong()).isZero();

        // The 3×3 matrix accounts for every record.
        long matrixTotal = 0;
        for (JsonNode row : report.get("matrix")) {
            for (JsonNode cell : row) {
                matrixTotal += cell.asLong();
            }
        }
        assertThat(matrixTotal).isEqualTo(n);

        // Every stored flip is an APPROVED → REFER or APPROVED → DECLINED move.
        JsonNode flips = read(getAs(STRATEGIST, "/api/v1/lab/replays/" + jobId + "/flips?size=5000").andReturn());
        assertThat(flips.get("items")).isNotEmpty();
        for (JsonNode flip : flips.get("items")) {
            assertThat(flip.get("baseline").get("outcome").asText()).isEqualTo("APPROVED");
            assertThat(flip.get("candidate").get("outcome").asText()).isIn("REFER", "DECLINED");
        }

        // A still-DRAFT version of this config becomes REPLAYED after a successful replay.
        String status = jdbc.queryForObject(
                "SELECT status FROM rule_version WHERE version = 'v1.4'", String.class);
        assertThat(status).isEqualTo("REPLAYED");
    }
}
