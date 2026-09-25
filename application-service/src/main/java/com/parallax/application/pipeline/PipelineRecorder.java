package com.parallax.application.pipeline;

import org.springframework.stereotype.Component;
import org.springframework.web.context.annotation.RequestScope;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;

import java.util.ArrayList;
import java.util.List;

/**
 * Request-scoped accumulator of server-measured pipeline timings (SPEC §3). The start nanos is set by
 * {@link PipelineStartFilter} as a servlet request attribute; each step records its own elapsed time.
 */
@Component
@RequestScope
public class PipelineRecorder {

    static final String START_ATTRIBUTE = "parallax.pipeline.startNanos";

    private final List<PipelineItem> items = new ArrayList<>();

    /** Nanos captured by the filter at the start of the request (falls back to now if absent). */
    public long startNanos() {
        Object value = RequestContextHolder.currentRequestAttributes()
                .getAttribute(START_ATTRIBUTE, RequestAttributes.SCOPE_REQUEST);
        return value instanceof Long nanos ? nanos : System.nanoTime();
    }

    /** Record a step measured from {@code sinceNanos} to now. */
    public void record(PipelineStep step, PipelineStatus status, long sinceNanos, String detail) {
        long ms = Math.max(0, (System.nanoTime() - sinceNanos) / 1_000_000);
        items.add(new PipelineItem(step.name(), step.label(), status.name(), ms, detail));
    }

    /** Record a step with a pre-measured elapsed time (ms). */
    public void recordMs(PipelineStep step, PipelineStatus status, long ms, String detail) {
        items.add(new PipelineItem(step.name(), step.label(), status.name(), Math.max(0, ms), detail));
    }

    /** Record a step with a custom label (e.g. ENGINE "Decision engine · v1.3") and pre-measured ms. */
    public void recordMs(PipelineStep step, String label, PipelineStatus status, long ms, String detail) {
        items.add(new PipelineItem(step.name(), label, status.name(), Math.max(0, ms), detail));
    }

    /** Record a step that was not executed (e.g. after a bureau failure). */
    public void skip(PipelineStep step, String detail) {
        items.add(new PipelineItem(step.name(), step.label(), PipelineStatus.SKIPPED.name(), 0, detail));
    }

    public List<PipelineItem> items() {
        return List.copyOf(items);
    }
}
