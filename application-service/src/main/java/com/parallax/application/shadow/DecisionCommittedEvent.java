package com.parallax.application.shadow;

import com.parallax.engine.model.Decision;
import com.parallax.engine.model.EngineInput;

/**
 * Published after a LIVE DECISION or REDECISION row commits with engine data (SPEC §10). The shadow
 * listener re-scores {@code engineInput} under the enabled shadow version and compares to
 * {@code liveDecision}. Carries no PII — only the ledger seq, the engine features and the live outcome.
 */
public record DecisionCommittedEvent(long ledgerSeq, EngineInput engineInput, Decision liveDecision) {
}
