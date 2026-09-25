package com.parallax.application.pipeline;

/** One measured pipeline step (SPEC §3): {step, label, status, ms, detail}. */
public record PipelineItem(String step, String label, String status, long ms, String detail) {
}
