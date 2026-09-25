package com.parallax.engine.model;

/**
 * The exact normalized input to the decision engine (SPEC §4). No identity fields, ever.
 * The compact constructor validates ranges so a malformed input never reaches the engine.
 */
public record EngineInput(
        int age,
        int birthYear,
        int annualIncome,
        int monthlyHousing,
        int monthlyDebt,
        double revolvingUtilization,
        int inquiries6m,
        int delinquencies24m,
        int openTradelines,
        int fileAgeMonths,
        boolean independentIncome,
        boolean bureauConsent,
        boolean addressMismatch,
        int ssnIssuanceYear,
        boolean deceased,
        int velocity24h,
        PullType pullType) {

    public EngineInput {
        if (age < 0 || age > 130) {
            throw new IllegalArgumentException("age must be between 0 and 130 (got " + age + ")");
        }
        if (birthYear < 1900 || birthYear > 2100) {
            throw new IllegalArgumentException("birthYear must be between 1900 and 2100 (got " + birthYear + ")");
        }
        if (annualIncome < 0) {
            throw new IllegalArgumentException("annualIncome must be >= 0 (got " + annualIncome + ")");
        }
        if (monthlyHousing < 0) {
            throw new IllegalArgumentException("monthlyHousing must be >= 0 (got " + monthlyHousing + ")");
        }
        if (monthlyDebt < 0) {
            throw new IllegalArgumentException("monthlyDebt must be >= 0 (got " + monthlyDebt + ")");
        }
        if (Double.isNaN(revolvingUtilization) || revolvingUtilization < 0.0 || revolvingUtilization > 1.0) {
            throw new IllegalArgumentException(
                    "revolvingUtilization must be between 0.0 and 1.0 (got " + revolvingUtilization + ")");
        }
        if (inquiries6m < 0) {
            throw new IllegalArgumentException("inquiries6m must be >= 0 (got " + inquiries6m + ")");
        }
        if (delinquencies24m < 0) {
            throw new IllegalArgumentException("delinquencies24m must be >= 0 (got " + delinquencies24m + ")");
        }
        if (openTradelines < 0) {
            throw new IllegalArgumentException("openTradelines must be >= 0 (got " + openTradelines + ")");
        }
        if (fileAgeMonths < 0) {
            throw new IllegalArgumentException("fileAgeMonths must be >= 0 (got " + fileAgeMonths + ")");
        }
        if (velocity24h < 0) {
            throw new IllegalArgumentException("velocity24h must be >= 0 (got " + velocity24h + ")");
        }
        if (ssnIssuanceYear < 1900 || ssnIssuanceYear > 2100) {
            throw new IllegalArgumentException(
                    "ssnIssuanceYear must be between 1900 and 2100 (got " + ssnIssuanceYear + ")");
        }
        if (pullType == null) {
            throw new IllegalArgumentException("pullType must be non-null");
        }
    }
}
