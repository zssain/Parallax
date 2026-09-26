package com.parallax.application.lab;

import com.parallax.engine.model.EngineInput;

/**
 * One replayable ledger row (SPEC §10): the stored engine input, the recorded outcome, and the known
 * loan outcome if any ({@code defaulted}/{@code simulated} are null when no {@code loan_outcome} row
 * exists). Only rows with a full EngineInput (bureau not UNAVAILABLE) are loaded.
 */
public record ReplayRow(long seq, EngineInput input, String recordedOutcome, String applicationPublicId,
                        Boolean defaulted, Boolean simulated) {

    boolean hasLoanOutcome() {
        return simulated != null;
    }

    boolean observed() {
        return hasLoanOutcome() && !simulated;
    }

    boolean didDefault() {
        return Boolean.TRUE.equals(defaulted);
    }

    /** Outcome class (SPEC §10): observed, immature (recent live approval), or unknown (reject inference). */
    OutcomeClass outcomeClass() {
        if (observed()) {
            return OutcomeClass.OBSERVED;
        }
        if (!hasLoanOutcome() && "APPROVED".equals(recordedOutcome)) {
            return OutcomeClass.IMMATURE;
        }
        return OutcomeClass.UNKNOWN;
    }

    enum OutcomeClass {
        OBSERVED, UNKNOWN, IMMATURE
    }
}
