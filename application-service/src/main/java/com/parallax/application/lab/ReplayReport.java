package com.parallax.application.lab;

import java.util.List;

/**
 * The honest Strategy Lab impact report (SPEC §10). Loss is PD × EAD × LGD over observed outcomes
 * only; applicants a looser rule newly approves are "outcome unknown" (reject inference), never
 * counted as safe. Money is whole-dollar exposure/loss rounded to the cent; each side uses its own
 * config's ccf and lgd.
 */
public record ReplayReport(
        long n,
        Side baseline,
        Candidate candidate,
        long[][] matrix,
        long flips,
        boolean flipsCapped,
        long limitChanges,
        Unknown outcomeUnknown,
        Immature immature,
        List<Segment> segments,
        Assumptions assumptions,
        Labels labels) {

    /** Baseline side of the report (the current LIVE config). */
    public record Side(String version, long approvals, double approvalRate, double exposure,
                       double expectedLossObserved) {
    }

    /** Candidate side, which also carries the simulated (reject-inference) loss labelled SIMULATION. */
    public record Candidate(String version, long approvals, double approvalRate, double exposure,
                            double expectedLossObserved, double expectedLossSimulated) {
    }

    /** Candidate approvals whose outcome was never observed (reject inference). */
    public record Unknown(long count, double exposure) {
    }

    /** Candidate approvals booked live but without 12 months of history yet. */
    public record Immature(long count) {
    }

    /** Impact by baseline score band (observed loss only). */
    public record Segment(String band, long n, long baselineApprovals, long candidateApprovals,
                          double baselineLoss, double candidateLoss) {
    }

    public record Assumptions(double ccf, double lgd, String pdSource) {
    }

    public record Labels(boolean simulatedIsSimulation) {
    }
}
