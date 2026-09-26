package com.parallax.application.lab;

import com.parallax.engine.model.Decision;

import java.util.List;

/** Response DTOs for the Strategy Lab read APIs (SPEC §15). */
public final class ReplayViews {

    private ReplayViews() {
    }

    /** {@code GET /api/v1/lab/replays/{jobId}}: job status, timings and the stored report (or null). */
    public record JobView(String jobId, String version, String baselineVersion, String status, Integer progress,
                          Integer total, Long loadMs, Long evaluateMs, Long totalMs, Object report, String error) {
    }

    /** {@code GET /api/v1/lab/replays/{jobId}/flips}: a page of flipped decisions. */
    public record FlipPage(List<FlipItem> items, int page, int size, long total) {
    }

    public record FlipItem(long seq, String applicationId, SideOutcome baseline, SideOutcome candidate,
                           List<String> candidateReasons, boolean observed) {
    }

    public record SideOutcome(String outcome, Integer score, Integer creditLimit) {
    }

    /** {@code GET /api/v1/lab/replays/{jobId}/flips/{seq}}: both full decisions and cutoffs. */
    public record FlipDetail(long seq, String applicationId, Object engineInput, boolean observed,
                             SideDecision baseline, SideDecision candidate) {
    }

    public record SideDecision(String version, int approveCutoff, int referCutoff, Decision decision) {
    }
}
