package com.parallax.bureau;

import java.math.BigDecimal;

/**
 * Maps a synthetic SSN to a credit profile and scenario (SPEC §8): the second digit selects the
 * profile, the third digit selects the scenario. All data is synthetic.
 */
public final class SyntheticProfiles {

    private SyntheticProfiles() {
    }

    public enum Profile {
        PRIME("0.080", 0, 0, 12, 156),
        NEAR_PRIME("0.550", 3, 0, 5, 40),
        SUBPRIME("0.820", 5, 2, 4, 30),
        THIN_FILE("0.200", 1, 0, 1, 10);

        private final BigDecimal utilization;
        private final int inquiries6m;
        private final int delinquencies24m;
        private final int openTradelines;
        private final int fileAgeMonths;

        Profile(String utilization, int inquiries6m, int delinquencies24m, int openTradelines, int fileAgeMonths) {
            this.utilization = new BigDecimal(utilization);
            this.inquiries6m = inquiries6m;
            this.delinquencies24m = delinquencies24m;
            this.openTradelines = openTradelines;
            this.fileAgeMonths = fileAgeMonths;
        }

        public BigDecimal utilization() {
            return utilization;
        }

        public int inquiries6m() {
            return inquiries6m;
        }

        public int delinquencies24m() {
            return delinquencies24m;
        }

        public int openTradelines() {
            return openTradelines;
        }

        public int fileAgeMonths() {
            return fileAgeMonths;
        }
    }

    public enum Scenario {
        NONE,
        ADDRESS_MISMATCH,
        SSN_BEFORE_DOB,
        DECEASED
    }

    /** Second digit → profile: 0–2 PRIME, 3–5 NEAR_PRIME, 6–7 SUBPRIME, 8 THIN_FILE, 9 PRIME. */
    public static Profile profileFor(String ssn) {
        return switch (digit(ssn, 1)) {
            case 0, 1, 2, 9 -> Profile.PRIME;
            case 3, 4, 5 -> Profile.NEAR_PRIME;
            case 6, 7 -> Profile.SUBPRIME;
            case 8 -> Profile.THIN_FILE;
            default -> throw new IllegalArgumentException("Invalid SSN profile digit");
        };
    }

    /** Third digit → scenario: 0–6 NONE, 7 ADDRESS_MISMATCH, 8 SSN_BEFORE_DOB, 9 DECEASED. */
    public static Scenario scenarioFor(String ssn) {
        return switch (digit(ssn, 2)) {
            case 0, 1, 2, 3, 4, 5, 6 -> Scenario.NONE;
            case 7 -> Scenario.ADDRESS_MISMATCH;
            case 8 -> Scenario.SSN_BEFORE_DOB;
            case 9 -> Scenario.DECEASED;
            default -> throw new IllegalArgumentException("Invalid SSN scenario digit");
        };
    }

    private static int digit(String ssn, int index) {
        char c = ssn.charAt(index);
        if (c < '0' || c > '9') {
            throw new IllegalArgumentException("SSN digit is not numeric");
        }
        return c - '0';
    }
}
