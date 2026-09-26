package com.parallax.application.lab;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** A looser candidate (approveCutoff 660) newly approves reject-inference cases: outcome unknown (SPEC §10). */
class ReplayLooseningIT extends AbstractLabIT {

    @Test
    void looserCutoffProducesUnknownOutcomesAndHonestLoss() throws Exception {
        seedHistory();
        insertCandidate("v1.4", withApproveCutoff(660));

        JsonNode report = pollDone(startReplay(STRATEGIST, "v1.4")).get("report");

        // Newly-approved records that were never booked (recorded REFER) are outcome-unknown.
        assertThat(report.get("outcomeUnknown").get("count").asLong()).isPositive();

        double observed = report.get("candidate").get("expectedLossObserved").asDouble();
        double simulated = report.get("candidate").get("expectedLossSimulated").asDouble();
        // Observed loss excludes the unknown approvals; the simulated figure adds their reject-inference loss.
        assertThat(simulated).isGreaterThanOrEqualTo(observed);
        // Observed loss counts only truly-observed outcomes, so a looser rule does not inflate it.
        double baselineObserved = report.get("baseline").get("expectedLossObserved").asDouble();
        assertThat(observed).isEqualTo(baselineObserved);
    }
}
