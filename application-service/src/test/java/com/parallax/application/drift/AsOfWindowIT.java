package com.parallax.application.drift;

import com.parallax.application.intake.AbstractIntakeIT;
import com.parallax.application.seed.HistorySeeder;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.time.LocalDate;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * With the as-of date two years in the future the current window is empty; PSI is still computed over
 * floored proportions (currentN 0 → every current proportion floors to 0.0001) without error (SPEC §11).
 */
class AsOfWindowIT extends AbstractIntakeIT {

    @Autowired
    HistorySeeder historySeeder;
    @Autowired
    DriftService driftService;

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry registry) {
        registry.add("parallax.seed.generate-count", () -> "2000");
        registry.add("parallax.as-of", () -> LocalDate.now(ZoneOffset.UTC).plusYears(2).toString());
    }

    @Test
    void futureAsOfLeavesCurrentWindowEmptyButPsiIsFinite() {
        driftService.clearBaselineCache();
        historySeeder.seed();
        driftService.clearBaselineCache();

        DriftViews.Report report = driftService.run();

        assertThat(report.currentN()).isZero();          // no applicants in (asOf−30d, asOf+1d]
        assertThat(report.baselineN()).isPositive();      // all seeded rows are older than asOf−90d
        assertThat(report.psi()).isFinite();
        assertThat(report.status()).isIn("stable", "watch", "investigate");
    }
}
