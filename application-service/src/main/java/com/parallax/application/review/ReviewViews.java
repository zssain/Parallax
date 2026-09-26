package com.parallax.application.review;

import java.time.Instant;
import java.util.List;

/** Response DTOs for the review queue, override stats and the record-decision result (SPEC §15). */
public final class ReviewViews {

    private ReviewViews() {
    }

    /** One row of the review queue (SPEC §15 {@code GET /api/v1/reviews/queue}). */
    public record QueueItem(String applicationId, String displayName, Integer score, String reasonKind,
                            List<String> reasonCodes, List<String> fraudFlags, Integer atpMax,
                            int suggestedLimit, Long baseSeq, Instant createdAt) {
    }

    /** The 201 body after recording an override (SPEC §15 {@code POST /api/v1/reviews/{id}}). */
    public record RecordResult(String applicationId, long seq, String outcome, Integer creditLimit) {
    }

    /** Override rate by score band (SPEC §15 {@code GET /api/v1/reviews/override-stats}). */
    public record OverrideStats(List<Band> bands) {
    }

    public record Band(String band, long refers, long overriddenToApprove, double rate) {
    }
}
