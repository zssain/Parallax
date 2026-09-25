package com.parallax.engine.model;

/**
 * Reason and fraud codes with their descriptions, verbatim from SPEC §4 "Reason texts".
 * Applicant-facing codes are the R* and P* codes; B01 and F01–F04 are internal only.
 */
public enum ReasonCode {
    R31("Proportion of revolving balances to credit limits is too high"),
    R14("Too many recent inquiries for credit"),
    R22("Delinquency on one or more accounts in the past 24 months"),
    R07("Insufficient number of established credit accounts"),
    R05("Length of credit history is too short"),
    R33("Income insufficient for the amount of credit requested"),
    P01("Income insufficient to support required minimum payments"),
    P02("Applicant does not meet the minimum age requirement"),
    P03("Applicant under 21 without independent income or co-signer"),
    P04("Consent for a credit bureau inquiry was not provided"),
    B01("Credit report temporarily unavailable (internal)"),
    F01("Address does not match the credit file (internal)"),
    F02("SSN issuance precedes date of birth (internal)"),
    F03("SSN reported deceased (internal)"),
    F04("Application velocity limit exceeded (internal)");

    private final String description;

    ReasonCode(String description) {
        this.description = description;
    }

    public String description() {
        return description;
    }

    /** True for the applicant-facing R* and P* codes; false for B01 and the fraud flags F01–F04. */
    public boolean applicantFacing() {
        return !isFraud() && this != B01;
    }

    /** True for the internal fraud flags F01–F04. */
    public boolean isFraud() {
        return this == F01 || this == F02 || this == F03 || this == F04;
    }
}
