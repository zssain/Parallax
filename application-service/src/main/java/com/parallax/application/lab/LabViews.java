package com.parallax.application.lab;

import com.parallax.engine.model.RuleConfig;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/** Response DTOs for the rule-version lifecycle, live rule, compare and rollback (SPEC §10, §15). */
public final class LabViews {

    private LabViews() {
    }

    /** The version cards, the current LIVE version, and the version a rollback would restore. */
    public record VersionsResponse(List<VersionView> items, String liveVersion, String rollbackTarget) {
    }

    /** One version card. {@code shadow} is false until Prompt 15 wires shadow mode. */
    public record VersionView(String version, String status, String note, String createdBy, Instant createdAt,
                              String proposedBy, String approvedBy, Instant promotedAt, long usedBy,
                              String latestReplayJobId, RuleConfig config, boolean shadow) {
    }

    /** The LIVE rule for the CLIENT decision path: version, the date it went live, and its config. */
    public record LiveView(String version, LocalDate since, RuleConfig config) {
    }

    /** Field-by-field differences between two configs; list fields flattened as {@code utilPts[3]}. */
    public record CompareView(String a, String b, List<Difference> differences) {
    }

    public record Difference(String field, Object a, Object b) {
    }

    /** Result of a rollback: the version retired and the version restored to LIVE. */
    public record RollbackView(String from, String to) {
    }
}
