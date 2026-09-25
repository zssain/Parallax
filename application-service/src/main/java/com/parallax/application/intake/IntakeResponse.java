package com.parallax.application.intake;

import com.parallax.application.pipeline.PipelineItem;
import com.parallax.engine.model.EngineInput;

import java.util.List;

/**
 * The interim intake response for Prompt 07 (stops at BUREAU_PULLED). Prompt 09 replaces this with the
 * full decision body.
 */
public record IntakeResponse(
        String applicationId,
        String status,
        List<PipelineItem> pipeline,
        // PX-9: remove
        EngineInput engineInputPreview) {
}
