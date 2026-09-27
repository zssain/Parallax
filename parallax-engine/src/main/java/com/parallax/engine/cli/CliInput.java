package com.parallax.engine.cli;

/**
 * The exact input to the credit-line-increase policy (SPEC §13). Pure data: the account-service builds
 * it from the account's statements and the LIVE rule config's affordability. The compact constructor
 * validates ranges so a malformed input never reaches the policy.
 *
 * @param currentLimit          the account's current credit limit (whole dollars)
 * @param requestedLimit        the requested new limit (whole dollars)
 * @param closedStatements      number of statements the account has closed (tenure)
 * @param onTimePaymentsLast12  of the last 12 due payments, how many were on time
 * @param paymentsDueLast12     how many payments were due in the last 12 statements (0..12)
 * @param utilization           balance / limit (0..∞ allowed, never NaN)
 * @param atpMax                the ability-to-pay maximum from the LIVE config (whole dollars)
 * @param currentlyDelinquent   whether the account is past due right now
 */
public record CliInput(
        int currentLimit,
        int requestedLimit,
        int closedStatements,
        int onTimePaymentsLast12,
        int paymentsDueLast12,
        double utilization,
        int atpMax,
        boolean currentlyDelinquent) {

    public CliInput {
        if (currentLimit < 0) {
            throw new IllegalArgumentException("currentLimit must be >= 0 (got " + currentLimit + ")");
        }
        if (requestedLimit < 0) {
            throw new IllegalArgumentException("requestedLimit must be >= 0 (got " + requestedLimit + ")");
        }
        if (closedStatements < 0) {
            throw new IllegalArgumentException("closedStatements must be >= 0 (got " + closedStatements + ")");
        }
        if (paymentsDueLast12 < 0 || paymentsDueLast12 > 12) {
            throw new IllegalArgumentException(
                    "paymentsDueLast12 must be between 0 and 12 (got " + paymentsDueLast12 + ")");
        }
        if (onTimePaymentsLast12 < 0 || onTimePaymentsLast12 > paymentsDueLast12) {
            throw new IllegalArgumentException("onTimePaymentsLast12 must be between 0 and paymentsDueLast12 ("
                    + paymentsDueLast12 + ") (got " + onTimePaymentsLast12 + ")");
        }
        if (Double.isNaN(utilization) || utilization < 0.0) {
            throw new IllegalArgumentException("utilization must be >= 0 and not NaN (got " + utilization + ")");
        }
        if (atpMax < 0) {
            throw new IllegalArgumentException("atpMax must be >= 0 (got " + atpMax + ")");
        }
    }
}
