package com.parallax.application.bureau;

/** The bureau numeric fields persisted as {@code bureau_pull.attributes} jsonb (SPEC §8), for reuse. */
public record BureauAttributes(
        String fileAddress,
        int ssnIssuanceYear,
        boolean deceased,
        int openTradelines,
        int inquiries6m,
        int delinquencies24m,
        double revolvingUtilization,
        int fileAgeMonths) {
}
