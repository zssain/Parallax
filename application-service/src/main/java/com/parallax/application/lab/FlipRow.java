package com.parallax.application.lab;

import java.util.List;

/** A single flipped decision, captured to {@code replay_flip} (SPEC §14), up to 5,000 per job. */
public record FlipRow(long seq, String applicationPublicId, String baselineOutcome, String candidateOutcome,
                      Integer baselineScore, Integer candidateScore, Integer baselineLimit,
                      Integer candidateLimit, List<String> candidateReasons, boolean observed) {
}
