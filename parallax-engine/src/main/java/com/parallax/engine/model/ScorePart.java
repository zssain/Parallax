package com.parallax.engine.model;

/** One scorecard attribute's contribution (SPEC §4). {@link #pointsLost()} = maxPoints − points. */
public record ScorePart(String attribute, String value, String band, int points, int maxPoints, ReasonCode code) {

    public int pointsLost() {
        return maxPoints - points;
    }
}
