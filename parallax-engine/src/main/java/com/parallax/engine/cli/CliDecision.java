package com.parallax.engine.cli;

import java.util.List;

/**
 * The result of a credit-line-increase decision (SPEC §13): the outcome, the new limit it resolves to
 * and the reasons (empty on a clean approval). The reasons list is copied and made immutable.
 */
public record CliDecision(CliOutcome outcome, int newLimit, List<String> reasons) {

    public CliDecision {
        reasons = List.copyOf(reasons);
    }
}
