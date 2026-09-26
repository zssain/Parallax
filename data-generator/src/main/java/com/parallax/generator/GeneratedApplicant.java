package com.parallax.generator;

import com.parallax.engine.model.EngineInput;

/**
 * One synthetic applicant: the pure {@link EngineInput} for the decision engine, its modelled
 * probability of default, and the drawn default outcome (SPEC §12 data, Prompt 12).
 */
public record GeneratedApplicant(EngineInput input, double pd, boolean defaulted) {
}
