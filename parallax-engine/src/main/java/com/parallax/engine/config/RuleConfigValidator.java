package com.parallax.engine.config;

import com.parallax.engine.model.BandLimit;
import com.parallax.engine.model.RuleConfig;

import java.util.ArrayList;
import java.util.List;

/**
 * Validates a {@link RuleConfig} against the rules in SPEC §4 "Config validation".
 * Returns one human-readable message per violated rule (empty list = valid); every message
 * names the field and the offending value. Array-length checks run before ordering checks so a
 * wrong length never causes an index error.
 */
public final class RuleConfigValidator {

    private static final int UTIL_LEN = 5;
    private static final int INQ_LEN = 4;
    private static final int DELQ_LEN = 3;
    private static final int TRADE_LEN = 4;
    private static final int FILE_LEN = 4;
    private static final int INCOME_LEN = 4;

    private RuleConfigValidator() {
    }

    public static List<String> validate(RuleConfig c) {
        List<String> errors = new ArrayList<>();

        // Cutoffs: range, then order.
        if (c.approveCutoff() < 300 || c.approveCutoff() > 850) {
            errors.add("approveCutoff must be between 300 and 850 (got " + c.approveCutoff() + ")");
        }
        if (c.referCutoff() < 300 || c.referCutoff() > 850) {
            errors.add("referCutoff must be between 300 and 850 (got " + c.referCutoff() + ")");
        }
        if (c.referCutoff() >= c.approveCutoff()) {
            errors.add("Cutoffs out of order: referCutoff (" + c.referCutoff()
                    + ") must be below approveCutoff (" + c.approveCutoff() + ")");
        }

        // Ratios and floors.
        if (c.atpShare() < 0.10 || c.atpShare() > 0.60) {
            errors.add("atpShare must be between 0.10 and 0.60 (got " + c.atpShare() + ")");
        }
        if (c.minPayPct() < 0.01 || c.minPayPct() > 0.05) {
            errors.add("minPayPct must be between 0.01 and 0.05 (got " + c.minPayPct() + ")");
        }
        if (c.livingCost() < 0) {
            errors.add("livingCost must be >= 0 (got " + c.livingCost() + ")");
        }
        if (c.minLimit() < 100) {
            errors.add("minLimit must be >= 100 (got " + c.minLimit() + ")");
        }

        validateBandLimits(c.bandLimits(), errors);

        // Array lengths first; a wrong length skips that array's ordering check.
        boolean utilOk = checkLength(c.utilPts(), UTIL_LEN, "utilPts", errors);
        boolean inqOk = checkLength(c.inqPts(), INQ_LEN, "inqPts", errors);
        boolean delqOk = checkLength(c.delqPts(), DELQ_LEN, "delqPts", errors);
        checkLength(c.tradelinePts(), TRADE_LEN, "tradelinePts", errors);
        boolean fileOk = checkLength(c.fileAgePts(), FILE_LEN, "fileAgePts", errors);
        boolean incomeOk = checkLength(c.incomePts(), INCOME_LEN, "incomePts", errors);

        // Ordering. utilPts, inqPts, delqPts non-increasing; fileAgePts, incomePts non-decreasing.
        // tradelinePts has no ordering rule (11+ is deliberately below 5–10).
        if (utilOk) {
            checkNonIncreasing(c.utilPts(), "utilPts", errors);
        }
        if (inqOk) {
            checkNonIncreasing(c.inqPts(), "inqPts", errors);
        }
        if (delqOk) {
            checkNonIncreasing(c.delqPts(), "delqPts", errors);
        }
        if (fileOk) {
            checkNonDecreasing(c.fileAgePts(), "fileAgePts", errors);
        }
        if (incomeOk) {
            checkNonDecreasing(c.incomePts(), "incomePts", errors);
        }

        // No negative points, in any array.
        checkNoNegative(c.utilPts(), "utilPts", errors);
        checkNoNegative(c.inqPts(), "inqPts", errors);
        checkNoNegative(c.delqPts(), "delqPts", errors);
        checkNoNegative(c.tradelinePts(), "tradelinePts", errors);
        checkNoNegative(c.fileAgePts(), "fileAgePts", errors);
        checkNoNegative(c.incomePts(), "incomePts", errors);

        // Sum of attribute maxima must be 550.
        int sum = maxOf(c.utilPts()) + maxOf(c.inqPts()) + maxOf(c.delqPts())
                + maxOf(c.tradelinePts()) + maxOf(c.fileAgePts()) + maxOf(c.incomePts());
        if (sum != 550) {
            errors.add("Sum of attribute maxima must be 550 (got " + sum + ")");
        }

        // Loss assumptions within 0..1.
        if (c.ccf() < 0.0 || c.ccf() > 1.0) {
            errors.add("ccf must be between 0 and 1 (got " + c.ccf() + ")");
        }
        if (c.lgd() < 0.0 || c.lgd() > 1.0) {
            errors.add("lgd must be between 0 and 1 (got " + c.lgd() + ")");
        }

        return errors;
    }

    private static void validateBandLimits(List<BandLimit> bands, List<String> errors) {
        if (bands.isEmpty()) {
            errors.add("bandLimits must not be empty");
            return;
        }
        for (int i = 1; i < bands.size(); i++) {
            if (bands.get(i).minScore() >= bands.get(i - 1).minScore()) {
                errors.add("bandLimits must be strictly descending by minScore (got " + minScores(bands) + ")");
                break;
            }
        }
        for (int i = 1; i < bands.size(); i++) {
            if (bands.get(i).limit() >= bands.get(i - 1).limit()) {
                errors.add("bandLimits must be strictly descending by limit (got " + limits(bands) + ")");
                break;
            }
        }
        int lastMinScore = bands.get(bands.size() - 1).minScore();
        if (lastMinScore != 0) {
            errors.add("bandLimits last minScore must be 0 (got " + lastMinScore + ")");
        }
        int topLimit = bands.get(0).limit();
        if (topLimit > 25000) {
            errors.add("bandLimits top limit must be <= 25,000 (got " + topLimit + ")");
        }
    }

    private static boolean checkLength(List<Integer> xs, int expected, String field, List<String> errors) {
        if (xs.size() != expected) {
            errors.add(field + " must have length " + expected + " (got " + xs.size() + ")");
            return false;
        }
        return true;
    }

    private static void checkNonIncreasing(List<Integer> xs, String field, List<String> errors) {
        for (int i = 1; i < xs.size(); i++) {
            if (xs.get(i) > xs.get(i - 1)) {
                errors.add(field + " must be non-increasing (got " + xs + ")");
                return;
            }
        }
    }

    private static void checkNonDecreasing(List<Integer> xs, String field, List<String> errors) {
        for (int i = 1; i < xs.size(); i++) {
            if (xs.get(i) < xs.get(i - 1)) {
                errors.add(field + " must be non-decreasing (got " + xs + ")");
                return;
            }
        }
    }

    private static void checkNoNegative(List<Integer> xs, String field, List<String> errors) {
        for (int x : xs) {
            if (x < 0) {
                errors.add(field + " must not contain negative points (got " + x + ")");
                return;
            }
        }
    }

    private static int maxOf(List<Integer> xs) {
        int max = 0;
        boolean first = true;
        for (int x : xs) {
            if (first || x > max) {
                max = x;
                first = false;
            }
        }
        return max;
    }

    private static String minScores(List<BandLimit> bands) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < bands.size(); i++) {
            if (i > 0) {
                sb.append(", ");
            }
            sb.append(bands.get(i).minScore());
        }
        return sb.append("]").toString();
    }

    private static String limits(List<BandLimit> bands) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < bands.size(); i++) {
            if (i > 0) {
                sb.append(", ");
            }
            sb.append(bands.get(i).limit());
        }
        return sb.append("]").toString();
    }
}
