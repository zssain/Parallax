package com.parallax.account.collections;

import java.util.List;

/** Delinquency buckets and collections priority (SPEC §13). */
final class Buckets {

    /** The four buckets, in summary order. */
    static final List<String> ALL = List.of("1-29", "30-59", "60-89", "90+");

    private Buckets() {
    }

    /** The bucket for a days-past-due value, or {@code null} when current (0). */
    static String of(int daysPastDue) {
        if (daysPastDue <= 0) {
            return null;
        }
        if (daysPastDue <= 29) {
            return "1-29";
        }
        if (daysPastDue <= 59) {
            return "30-59";
        }
        if (daysPastDue <= 89) {
            return "60-89";
        }
        return "90+";
    }

    /** HIGH if ≥ 60 DPD or balance > $1,000 (balance_cents > 100000); MEDIUM if ≥ 30 DPD; else LOW. */
    static String priority(int daysPastDue, long balanceCents) {
        if (daysPastDue >= 60 || balanceCents > 100000) {
            return "HIGH";
        }
        if (daysPastDue >= 30) {
            return "MEDIUM";
        }
        return "LOW";
    }
}
