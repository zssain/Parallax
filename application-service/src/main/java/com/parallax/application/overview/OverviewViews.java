package com.parallax.application.overview;

import com.parallax.application.query.ApplicationViews;

import java.time.LocalDate;
import java.util.List;

/** Response DTOs for the Overview landing screen (SPEC §15). */
public final class OverviewViews {

    private OverviewViews() {
    }

    public record Overview(long decisions, long approved, long refer, long declined, double approvalRate,
                           long reviewQueue, LiveVersion liveVersion, Psi psi, String bureauCircuit,
                           long redecisionQueue, List<Proposed> proposedVersions, String shadowVersion,
                           List<TrendPoint> approvalTrend, List<ApplicationViews.ListItem> recent,
                           List<Attention> attention) {
    }

    public record LiveVersion(String version, LocalDate since) {
    }

    /** The latest drift PSI and its status; the whole object is null when no report exists. */
    public record Psi(double value, String status) {
    }

    public record Proposed(String version, String proposedBy) {
    }

    /** Approval rate for a calendar month; {@code rate} is null when the month had no decisions. */
    public record TrendPoint(String month, Double rate) {
    }

    public record Attention(String message, String target, String severity) {
    }
}
