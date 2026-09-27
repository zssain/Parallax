package com.parallax.engine.cli;

import java.util.ArrayList;
import java.util.List;

/**
 * The pure credit-line-increase policy (SPEC §13). Same input always produces the same decision — no
 * state, time, I/O or randomness, exactly like the decision engine.
 *
 * <p>Eligibility gates are checked in this order, collecting every failed reason; any failure declines
 * the request at the current limit. Otherwise the account qualifies for
 * {@code maxAllowed = floor(min(atpMax, 2 × currentLimit, 25000) / 100) × 100}: a request within it is
 * approved as-is, a request above it is countered at {@code maxAllowed} when that is above the current
 * limit, and otherwise there is no room to grow and the request is declined.
 */
public final class CliPolicy {

    /** Whole-dollar ceiling on any single credit line (SPEC §13). */
    private static final int MAX_LINE = 25_000;

    private CliPolicy() {
    }

    public static CliDecision evaluate(CliInput in) {
        List<String> reasons = new ArrayList<>();
        if (in.currentlyDelinquent()) {
            reasons.add("Account is past due");
        }
        if (in.closedStatements() < 6) {
            reasons.add("Account open less than 6 months");
        }
        // On-time rule: when at least 6 payments were due, at least 10-of-12 (a 10/12 ratio) must have
        // been on time. Checked with exact integer cross-multiplication (12·onTime < 10·due) — see
        // docs/DECISIONS.md.
        if (in.paymentsDueLast12() >= 6
                && 12L * in.onTimePaymentsLast12() < 10L * in.paymentsDueLast12()) {
            reasons.add("Fewer than 10 of 12 payments on time");
        }
        if (in.utilization() > 0.90) {
            reasons.add("Utilization above 90%");
        }
        if (!reasons.isEmpty()) {
            return new CliDecision(CliOutcome.DECLINED, in.currentLimit(), reasons);
        }

        long cap = Math.min(in.atpMax(), Math.min(2L * in.currentLimit(), MAX_LINE));
        int maxAllowed = (int) (cap / 100 * 100); // floor to the nearest $100 (cap is non-negative)

        if (in.requestedLimit() <= maxAllowed) {
            return new CliDecision(CliOutcome.APPROVED, in.requestedLimit(), List.of());
        }
        if (maxAllowed > in.currentLimit()) {
            return new CliDecision(CliOutcome.COUNTER_OFFER, maxAllowed,
                    List.of("Requested limit exceeds what the account qualifies for"));
        }
        return new CliDecision(CliOutcome.DECLINED, in.currentLimit(),
                List.of("Not eligible for a higher limit"));
    }
}
