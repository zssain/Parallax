package com.parallax.application.drift;

import java.util.Arrays;

/**
 * Population Stability Index over the seven score bins (SPEC §11). Pure: bin boundaries, floored
 * proportions and the PSI sum are deterministic. Proportions are floored at 0.0001 so an empty bin
 * (or an empty window) never yields ln(0) or a division by zero.
 */
public final class PsiCalculator {

    /** The seven score bins, inclusive [from, to] (SPEC §11). */
    public static final int[][] BINS = {
            {300, 579}, {580, 619}, {620, 659}, {660, 699}, {700, 739}, {740, 779}, {780, 850}
    };

    private static final double FLOOR = 0.0001;

    private PsiCalculator() {
    }

    /** The bin index for a score (scores are always 300–850). */
    public static int binIndex(int score) {
        if (score < 580) {
            return 0;
        }
        if (score < 620) {
            return 1;
        }
        if (score < 660) {
            return 2;
        }
        if (score < 700) {
            return 3;
        }
        if (score < 740) {
            return 4;
        }
        if (score < 780) {
            return 5;
        }
        return 6;
    }

    /** Per-bin proportions of the counts, each floored at 0.0001 (SPEC §11). */
    public static double[] proportions(long[] counts) {
        long total = Arrays.stream(counts).sum();
        double[] proportions = new double[counts.length];
        for (int i = 0; i < counts.length; i++) {
            double proportion = total > 0 ? (double) counts[i] / total : 0.0;
            proportions[i] = Math.max(proportion, FLOOR);
        }
        return proportions;
    }

    /** Per-bin PSI contribution: (current − baseline) × ln(current / baseline) over floored proportions. */
    public static double[] contributions(long[] baseline, long[] current) {
        double[] b = proportions(baseline);
        double[] c = proportions(current);
        double[] contributions = new double[baseline.length];
        for (int i = 0; i < contributions.length; i++) {
            contributions[i] = (c[i] - b[i]) * Math.log(c[i] / b[i]);
        }
        return contributions;
    }

    /** Total PSI = Σ of the per-bin contributions. */
    public static double psi(long[] baseline, long[] current) {
        double psi = 0.0;
        for (double contribution : contributions(baseline, current)) {
            psi += contribution;
        }
        return psi;
    }

    /** Status label (SPEC §11): &lt; 0.10 stable, 0.10–0.25 watch, &gt; 0.25 investigate. */
    public static String status(double psi) {
        if (psi < 0.10) {
            return "stable";
        }
        if (psi <= 0.25) {
            return "watch";
        }
        return "investigate";
    }
}
