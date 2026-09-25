package com.parallax.engine.api;

import com.parallax.engine.model.Decision;

/**
 * The evaluate response contract shared by application-service and decision-service (SPEC §15).
 * A plain record with no annotations, mirroring {@link EvaluateRequest}.
 */
public record EvaluateResponse(String ruleVersion, String engineVersion, String scorecardVersion,
                               Decision decision) {
}
