package com.parallax.application.decision;

import com.parallax.application.pipeline.PipelineItem;

import java.util.List;

/** The 202 body returned when the engine is unavailable and the application is queued for retry (SPEC §3 7c). */
public record EnginePendingResponse(String applicationId, String status, List<PipelineItem> pipeline) {
}
