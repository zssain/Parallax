package com.parallax.engine.scoring;

/** Score-band labels for replay segmentation (SPEC §10). */
public final class Segments {

    private Segments() {
    }

    public static String scoreBand(int score) {
        if (score < 620) {
            return "<620";
        }
        if (score < 680) {
            return "620–679";
        }
        if (score < 720) {
            return "680–719";
        }
        if (score < 760) {
            return "720–759";
        }
        return "760+";
    }
}
