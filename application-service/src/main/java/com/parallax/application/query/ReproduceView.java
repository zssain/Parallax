package com.parallax.application.query;

import java.util.List;

/** {@code GET /api/v1/decisions/{seq}/reproduce} response (SPEC §15). */
public record ReproduceView(long seq, String ruleVersion, Boolean identical, String message,
                            Outcome stored, Outcome recomputed) {

    public record Outcome(String outcome, Integer score, Integer creditLimit, List<String> reasonCodes) {
    }
}
