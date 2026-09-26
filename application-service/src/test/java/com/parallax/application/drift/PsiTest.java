package com.parallax.application.drift;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** PSI maths and status thresholds (SPEC §11). Pure — no database. */
class PsiTest {

    @Test
    void handComputedPsiMatches() {
        // Baseline uniform (1/7 each); current puts twice the mass in bin 0 (20/80), the rest 10/80.
        long[] baseline = {10, 10, 10, 10, 10, 10, 10};
        long[] current = {20, 10, 10, 10, 10, 10, 10};

        // Bin 0: (0.25 − 1/7)·ln(0.25 / (1/7)) = 0.0599588
        // Bins 1–6: (0.125 − 1/7)·ln(0.125 / (1/7)) = 0.0023845 each → ×6 = 0.0143069
        // Total ≈ 0.0742658
        assertThat(PsiCalculator.psi(baseline, current)).isCloseTo(0.0742658, org.assertj.core.data.Offset.offset(0.0001));
    }

    @Test
    void thresholdsMapToStatus() {
        assertThat(PsiCalculator.status(0.0999)).isEqualTo("stable");
        assertThat(PsiCalculator.status(0.10)).isEqualTo("watch");
        assertThat(PsiCalculator.status(0.25)).isEqualTo("watch");
        assertThat(PsiCalculator.status(0.2501)).isEqualTo("investigate");
    }

    @Test
    void emptyCurrentWindowFloorsProportionsWithoutError() {
        long[] baseline = {100, 100, 100, 100, 100, 100, 100};
        long[] current = {0, 0, 0, 0, 0, 0, 0};

        double psi = PsiCalculator.psi(baseline, current);
        assertThat(psi).isFinite().isPositive();
        assertThat(PsiCalculator.status(psi)).isIn("stable", "watch", "investigate");
    }
}
