package com.parallax.application.review;

/** Underwriter override reason codes (SPEC §4). Each carries its applicant-neutral description. */
public enum OverrideCode {

    O1("Identity verified with documents"),
    O2("Income verified"),
    O3("Fraud confirmed"),
    O4("Bureau data corrected"),
    O5("Credit policy exception");

    private final String description;

    OverrideCode(String description) {
        this.description = description;
    }

    public String description() {
        return description;
    }
}
