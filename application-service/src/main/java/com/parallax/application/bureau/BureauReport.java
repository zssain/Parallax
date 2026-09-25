package com.parallax.application.bureau;

/** The normalized result of a bureau pull (fresh or reused), consumed by feature derivation (SPEC §8). */
public record BureauReport(
        String pullId,
        String pullType,
        boolean reused,
        String profile,
        String fileAddress,
        int ssnIssuanceYear,
        boolean deceased,
        int openTradelines,
        int inquiries6m,
        int delinquencies24m,
        double revolvingUtilization,
        int fileAgeMonths,
        String rawXml) {
}
