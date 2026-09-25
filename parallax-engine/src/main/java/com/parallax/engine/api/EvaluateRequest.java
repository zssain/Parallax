package com.parallax.engine.api;

import com.parallax.engine.model.EngineInput;
import com.parallax.engine.model.RuleConfig;

/**
 * The evaluate request contract shared by application-service and decision-service (SPEC §15).
 * A plain record with no annotations — the engine stays framework-free; callers bind it with their
 * own Jackson (records bind by parameter name, requiring {@code -parameters} at compile time).
 */
public record EvaluateRequest(String ruleVersion, RuleConfig config, EngineInput input) {
}
