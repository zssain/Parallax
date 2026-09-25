package com.parallax.engine.config;

import com.parallax.engine.model.BandLimit;
import com.parallax.engine.model.RuleConfig;

import java.util.List;

/** The canonical rule configurations from SPEC §4. */
public final class RuleConfigs {

    private RuleConfigs() {
    }

    /** v1.3 — LIVE at start. */
    public static RuleConfig v1_3() {
        return new RuleConfig(
                680,        // approveCutoff
                620,        // referCutoff
                0.03,       // minPayPct
                0.35,       // atpShare
                1200,       // livingCost
                300,        // minLimit
                List.of(
                        new BandLimit(800, 12000),
                        new BandLimit(760, 7500),
                        new BandLimit(720, 4000),
                        new BandLimit(680, 2000),
                        new BandLimit(0, 1000)),
                List.of(130, 115, 85, 45, 10),   // utilPts
                List.of(90, 70, 35, 5),          // inqPts
                List.of(140, 60, 10),            // delqPts
                List.of(20, 55, 70, 60),         // tradelinePts
                List.of(15, 40, 60, 70),         // fileAgePts
                List.of(10, 25, 40, 50),         // incomePts
                0.6,        // ccf
                0.9);       // lgd
    }

    /** v1.2 — RETIRED. As v1.3 except approveCutoff 670 and utilPts [130,110,80,50,15]. */
    public static RuleConfig v1_2() {
        return new RuleConfig(
                670,        // approveCutoff
                620,        // referCutoff
                0.03,       // minPayPct
                0.35,       // atpShare
                1200,       // livingCost
                300,        // minLimit
                List.of(
                        new BandLimit(800, 12000),
                        new BandLimit(760, 7500),
                        new BandLimit(720, 4000),
                        new BandLimit(680, 2000),
                        new BandLimit(0, 1000)),
                List.of(130, 110, 80, 50, 15),   // utilPts
                List.of(90, 70, 35, 5),          // inqPts
                List.of(140, 60, 10),            // delqPts
                List.of(20, 55, 70, 60),         // tradelinePts
                List.of(15, 40, 60, 70),         // fileAgePts
                List.of(10, 25, 40, 50),         // incomePts
                0.6,        // ccf
                0.9);       // lgd
    }
}
