package com.parallax.application.lab;

import com.parallax.engine.model.Decision;
import com.parallax.engine.model.Outcome;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** The expected-loss maths, to the cent (SPEC §10). ccf 0.6, lgd 0.9 → factor 0.54 per approved default. */
class ExpectedLossTest {

    @Test
    void expectedLossObservedIsExactToTheCent() {
        ReportAccumulator acc = new ReportAccumulator(0.6, 0.9, 0.6, 0.9);
        // Observed, defaulted: 10000 × 0.54 = 5400.00
        acc.add(observedRow(1, true), approved(720, 10000), approved(720, 10000));
        // Observed, repaid: contributes nothing
        acc.add(observedRow(2, false), approved(700, 5000), approved(700, 5000));
        // Observed, defaulted: 2000 × 0.54 = 1080.00
        acc.add(observedRow(3, true), approved(690, 2000), approved(690, 2000));

        ReplayReport report = acc.toReport("v1.3", "v1.3");
        assertThat(report.baseline().expectedLossObserved()).isEqualTo(6480.00);
        assertThat(report.candidate().expectedLossObserved()).isEqualTo(6480.00);
        // No unknown approvals → simulated equals observed.
        assertThat(report.candidate().expectedLossSimulated()).isEqualTo(6480.00);
        // Exposure = Σ limit × ccf = (10000 + 5000 + 2000) × 0.6 = 10200.00
        assertThat(report.candidate().exposure()).isEqualTo(10200.00);
    }

    private static ReplayRow observedRow(long seq, boolean defaulted) {
        // simulated=false → an observed outcome; recorded APPROVED.
        return new ReplayRow(seq, null, "APPROVED", "APP-" + seq, defaulted, false);
    }

    private static Decision approved(int score, int limit) {
        return new Decision(Outcome.APPROVED, score, limit, List.of(), List.of(), List.of(), List.of(), 0);
    }
}
