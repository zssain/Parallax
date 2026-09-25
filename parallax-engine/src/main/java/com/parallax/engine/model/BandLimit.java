package com.parallax.engine.model;

/** One credit-limit band: the minimum score to qualify and the limit granted (SPEC §4). */
public record BandLimit(int minScore, int limit) {
}
