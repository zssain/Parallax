package com.parallax.engine.model;

import java.util.List;

/**
 * The full result of an engine evaluation (SPEC §4). Reason codes are applicant-facing plus
 * policy codes; fraud flags are returned separately. Every list is copied immutably.
 */
public record Decision(
        Outcome outcome,
        int score,
        int creditLimit,
        List<ReasonCode> reasonCodes,
        List<ReasonCode> fraudFlags,
        List<PolicyCheck> policyChecks,
        List<ScorePart> scoreParts,
        int atpMax) {

    public Decision {
        reasonCodes = List.copyOf(reasonCodes);
        fraudFlags = List.copyOf(fraudFlags);
        policyChecks = List.copyOf(policyChecks);
        scoreParts = List.copyOf(scoreParts);
    }
}
