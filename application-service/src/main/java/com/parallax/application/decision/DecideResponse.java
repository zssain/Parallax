package com.parallax.application.decision;

import com.parallax.application.pipeline.PipelineItem;

import java.time.Instant;
import java.util.List;

/** The 201 decision response (SPEC §15). Fraud flags never appear here; reason codes are applicant-facing only. */
public record DecideResponse(
        String applicationId,
        String status,
        String outcome,
        Integer score,
        Integer creditLimit,
        List<ReasonCodeView> reasonCodes,
        String ruleVersion,
        long ledgerSeq,
        Instant decidedAt,
        BureauView bureau,
        List<PipelineItem> pipeline) {

    public record ReasonCodeView(String code, String description) {
    }

    public record BureauView(String pullId, boolean reused) {
    }
}
