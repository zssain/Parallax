package com.parallax.application.drift;

import com.parallax.application.intake.AbstractIntakeIT;
import com.parallax.application.seed.HistorySeeder;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

/** Drift over seeded history: a stored report, then simulation where a bigger shift means a bigger PSI. */
class DriftIT extends AbstractIntakeIT {

    @Autowired
    HistorySeeder historySeeder;
    @Autowired
    DriftService driftService;

    @DynamicPropertySource
    static void seedProps(DynamicPropertyRegistry registry) {
        registry.add("parallax.seed.generate-count", () -> "3000");
    }

    @Test
    void runsStoresAndSimulates() {
        driftService.clearBaselineCache();
        historySeeder.seed();
        driftService.clearBaselineCache(); // baseline must reflect the freshly seeded rows

        DriftViews.Report report = driftService.run();
        assertThat(report.id()).isNotNull();
        assertThat(report.baselineN()).isPositive();
        assertThat(report.currentN()).isPositive();
        assertThat(report.simulated()).isFalse();
        assertThat(report.bins()).hasSize(7);

        DriftViews.Report latest = driftService.latest();
        assertThat(latest.id()).isEqualTo(report.id());

        int storedBefore = count("drift_report");
        DriftViews.Report calm = driftService.simulate(0.0);
        DriftViews.Report shifted = driftService.simulate(1.5);

        assertThat(calm.simulated()).isTrue();
        assertThat(calm.id()).isNull();
        assertThat(calm.currentN()).isEqualTo(5000);
        assertThat(shifted.currentN()).isEqualTo(5000);
        assertThat(shifted.psi()).isGreaterThan(calm.psi());
        assertThat(count("drift_report")).isEqualTo(storedBefore); // neither simulation is stored
    }
}
