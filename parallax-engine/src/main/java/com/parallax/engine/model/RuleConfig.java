package com.parallax.engine.model;

import java.util.List;

/**
 * A complete rule configuration (SPEC §4). The compact constructor takes a defensive,
 * immutable copy of every list; it does NOT validate the rules — that is
 * {@code com.parallax.engine.config.RuleConfigValidator}'s job.
 */
public record RuleConfig(
        int approveCutoff,
        int referCutoff,
        double minPayPct,
        double atpShare,
        int livingCost,
        int minLimit,
        List<BandLimit> bandLimits,
        List<Integer> utilPts,
        List<Integer> inqPts,
        List<Integer> delqPts,
        List<Integer> tradelinePts,
        List<Integer> fileAgePts,
        List<Integer> incomePts,
        double ccf,
        double lgd) {

    public RuleConfig {
        bandLimits = List.copyOf(bandLimits);
        utilPts = List.copyOf(utilPts);
        inqPts = List.copyOf(inqPts);
        delqPts = List.copyOf(delqPts);
        tradelinePts = List.copyOf(tradelinePts);
        fileAgePts = List.copyOf(fileAgePts);
        incomePts = List.copyOf(incomePts);
    }
}
