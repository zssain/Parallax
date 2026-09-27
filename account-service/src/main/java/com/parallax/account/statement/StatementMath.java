package com.parallax.account.statement;

/**
 * Pure statement arithmetic (SPEC §13). Interest and minimum due are computed in whole cents so the
 * figures are exact and unit-testable without a database.
 */
public final class StatementMath {

    private static final long MIN_PAYMENT_FLOOR_CENTS = 2500;

    private StatementMath() {
    }

    /** interest = round(balance × APR_bps / 10000 / 12); zero when the balance is not positive. */
    public static long interest(long balanceCents, int aprBps) {
        if (balanceCents <= 0) {
            return 0;
        }
        return Math.round(balanceCents * (double) aprBps / 10000.0 / 12.0);
    }

    /**
     * minimum due = balance ≤ 0 ? 0 : min(balance, max(2500, round(balance × 1%) + interest)), where
     * {@code balanceCents} is the balance AFTER interest has been added for the cycle (SPEC §13).
     */
    public static long minimumDue(long balanceCents, long interestCents) {
        if (balanceCents <= 0) {
            return 0;
        }
        long onePercent = Math.round(balanceCents * 0.01);
        return Math.min(balanceCents, Math.max(MIN_PAYMENT_FLOOR_CENTS, onePercent + interestCents));
    }
}
