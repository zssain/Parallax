package com.parallax.application.lab;

import com.parallax.engine.model.Decision;
import com.parallax.engine.model.Outcome;
import com.parallax.engine.model.ReasonCode;
import com.parallax.engine.scoring.Segments;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Accumulates the impact report for a slice of records (SPEC §10). One instance fills per chunk; chunks
 * merge in chunk order so the result is deterministic. Loss and exposure use each side's own ccf/lgd;
 * expected loss counts observed outcomes only, with a separate simulated (reject-inference) figure.
 */
final class ReportAccumulator {

    static final int FLIP_CAP = 5000;
    private static final List<String> BANDS = List.of("<620", "620–679", "680–719", "720–759", "760+");

    private final double baselineCcf;
    private final double baselineLgd;
    private final double candidateCcf;
    private final double candidateLgd;

    private long n;
    private long baselineApprovals;
    private long candidateApprovals;
    private final long[][] matrix = new long[3][3];
    private long flips;
    private long limitChanges;
    private double baselineExposure;
    private double candidateExposure;
    private double baselineLossObserved;
    private double candidateLossObserved;
    private double candidateSimulatedExtra;
    private long unknownCount;
    private double unknownExposure;
    private long immatureCount;
    private final Map<String, SegmentAcc> segments = new LinkedHashMap<>();
    private final List<FlipRow> flipList = new ArrayList<>();

    ReportAccumulator(double baselineCcf, double baselineLgd, double candidateCcf, double candidateLgd) {
        this.baselineCcf = baselineCcf;
        this.baselineLgd = baselineLgd;
        this.candidateCcf = candidateCcf;
        this.candidateLgd = candidateLgd;
    }

    void add(ReplayRow row, Decision baseline, Decision candidate) {
        n++;
        int bi = index(baseline.outcome());
        int ci = index(candidate.outcome());
        matrix[bi][ci]++;

        boolean bApproved = baseline.outcome() == Outcome.APPROVED;
        boolean cApproved = candidate.outcome() == Outcome.APPROVED;
        if (bApproved) {
            baselineApprovals++;
            baselineExposure += baseline.creditLimit() * baselineCcf;
        }
        if (cApproved) {
            candidateApprovals++;
            candidateExposure += candidate.creditLimit() * candidateCcf;
        }
        if (bApproved && cApproved && baseline.creditLimit() != candidate.creditLimit()) {
            limitChanges++;
        }

        ReplayRow.OutcomeClass cls = row.outcomeClass();
        boolean observed = cls == ReplayRow.OutcomeClass.OBSERVED;
        boolean defaulted = row.didDefault();

        if (bApproved && observed && defaulted) {
            baselineLossObserved += loss(baseline.creditLimit(), baselineCcf, baselineLgd);
        }
        if (cApproved && observed && defaulted) {
            candidateLossObserved += loss(candidate.creditLimit(), candidateCcf, candidateLgd);
        }
        if (cApproved) {
            if (cls == ReplayRow.OutcomeClass.UNKNOWN) {
                unknownCount++;
                unknownExposure += candidate.creditLimit() * candidateCcf;
                if (row.hasLoanOutcome() && Boolean.TRUE.equals(row.simulated()) && defaulted) {
                    candidateSimulatedExtra += loss(candidate.creditLimit(), candidateCcf, candidateLgd);
                }
            } else if (cls == ReplayRow.OutcomeClass.IMMATURE) {
                immatureCount++;
            }
        }

        SegmentAcc seg = segments.computeIfAbsent(Segments.scoreBand(baseline.score()), k -> new SegmentAcc());
        seg.n++;
        if (bApproved) {
            seg.baselineApprovals++;
            if (observed && defaulted) {
                seg.baselineLoss += loss(baseline.creditLimit(), baselineCcf, baselineLgd);
            }
        }
        if (cApproved) {
            seg.candidateApprovals++;
            if (observed && defaulted) {
                seg.candidateLoss += loss(candidate.creditLimit(), candidateCcf, candidateLgd);
            }
        }

        if (bi != ci) {
            flips++;
            if (flipList.size() < FLIP_CAP) {
                flipList.add(new FlipRow(row.seq(), row.applicationPublicId(), baseline.outcome().name(),
                        candidate.outcome().name(), baseline.score(), candidate.score(),
                        baseline.creditLimit(), candidate.creditLimit(), names(candidate.reasonCodes()), observed));
            }
        }
    }

    /** Merge another accumulator (same configs) in chunk order; caps the flip list globally at 5,000. */
    void merge(ReportAccumulator other) {
        n += other.n;
        baselineApprovals += other.baselineApprovals;
        candidateApprovals += other.candidateApprovals;
        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < 3; j++) {
                matrix[i][j] += other.matrix[i][j];
            }
        }
        flips += other.flips;
        limitChanges += other.limitChanges;
        baselineExposure += other.baselineExposure;
        candidateExposure += other.candidateExposure;
        baselineLossObserved += other.baselineLossObserved;
        candidateLossObserved += other.candidateLossObserved;
        candidateSimulatedExtra += other.candidateSimulatedExtra;
        unknownCount += other.unknownCount;
        unknownExposure += other.unknownExposure;
        immatureCount += other.immatureCount;
        other.segments.forEach((band, seg) -> segments.computeIfAbsent(band, k -> new SegmentAcc()).addAll(seg));
        for (FlipRow flip : other.flipList) {
            if (flipList.size() < FLIP_CAP) {
                flipList.add(flip);
            }
        }
    }

    List<FlipRow> flips() {
        return flipList;
    }

    ReplayReport toReport(String baselineVersion, String candidateVersion) {
        ReplayReport.Side baseline = new ReplayReport.Side(baselineVersion, baselineApprovals,
                rate(baselineApprovals), money(baselineExposure), money(baselineLossObserved));
        ReplayReport.Candidate candidate = new ReplayReport.Candidate(candidateVersion, candidateApprovals,
                rate(candidateApprovals), money(candidateExposure), money(candidateLossObserved),
                money(candidateLossObserved + candidateSimulatedExtra));

        long[][] matrixCopy = new long[3][3];
        for (int i = 0; i < 3; i++) {
            System.arraycopy(matrix[i], 0, matrixCopy[i], 0, 3);
        }

        List<ReplayReport.Segment> segs = new ArrayList<>();
        for (String band : BANDS) {
            SegmentAcc s = segments.getOrDefault(band, new SegmentAcc());
            segs.add(new ReplayReport.Segment(band, s.n, s.baselineApprovals, s.candidateApprovals,
                    money(s.baselineLoss), money(s.candidateLoss)));
        }

        return new ReplayReport(n, baseline, candidate, matrixCopy, flips, flips > FLIP_CAP, limitChanges,
                new ReplayReport.Unknown(unknownCount, money(unknownExposure)),
                new ReplayReport.Immature(immatureCount), segs,
                new ReplayReport.Assumptions(candidateCcf, candidateLgd, "observed synthetic outcomes"),
                new ReplayReport.Labels(true));
    }

    private double rate(long approvals) {
        return n == 0 ? 0.0 : round(approvals / (double) n, 4);
    }

    private static double loss(int limit, double ccf, double lgd) {
        return limit * ccf * lgd;
    }

    private static double money(double value) {
        return round(value, 2);
    }

    private static double round(double value, int scale) {
        return BigDecimal.valueOf(value).setScale(scale, RoundingMode.HALF_UP).doubleValue();
    }

    private static int index(Outcome outcome) {
        return switch (outcome) {
            case APPROVED -> 0;
            case REFER -> 1;
            case DECLINED -> 2;
        };
    }

    private static List<String> names(List<ReasonCode> codes) {
        return codes.stream().map(Enum::name).toList();
    }

    private static final class SegmentAcc {
        private long n;
        private long baselineApprovals;
        private long candidateApprovals;
        private double baselineLoss;
        private double candidateLoss;

        void addAll(SegmentAcc other) {
            n += other.n;
            baselineApprovals += other.baselineApprovals;
            candidateApprovals += other.candidateApprovals;
            baselineLoss += other.baselineLoss;
            candidateLoss += other.candidateLoss;
        }
    }
}
