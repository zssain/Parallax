package com.parallax.engine.cli;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** One worked example per branch of the CLI policy (SPEC §13). */
class CliPolicyTest {

    @Test
    void delinquentAccountIsDeclined() {
        CliDecision d = CliPolicy.evaluate(new CliInput(2000, 3000, 12, 12, 12, 0.30, 10000, true));
        assertThat(d.outcome()).isEqualTo(CliOutcome.DECLINED);
        assertThat(d.newLimit()).isEqualTo(2000);
        assertThat(d.reasons()).containsExactly("Account is past due");
    }

    @Test
    void tooFewClosedStatementsIsDeclined() {
        CliDecision d = CliPolicy.evaluate(new CliInput(2000, 3000, 5, 0, 0, 0.30, 10000, false));
        assertThat(d.outcome()).isEqualTo(CliOutcome.DECLINED);
        assertThat(d.newLimit()).isEqualTo(2000);
        assertThat(d.reasons()).containsExactly("Account open less than 6 months");
    }

    @Test
    void belowTenOfTwelveOnTimeIsDeclined() {
        CliDecision d = CliPolicy.evaluate(new CliInput(2000, 3000, 12, 9, 12, 0.30, 10000, false));
        assertThat(d.outcome()).isEqualTo(CliOutcome.DECLINED);
        assertThat(d.reasons()).containsExactly("Fewer than 10 of 12 payments on time");
    }

    @Test
    void exactlyTenOfTwelveOnTimePassesTheOnTimeGate() {
        // 10 of 12 on time is the boundary: it must NOT trip the on-time reason.
        CliDecision d = CliPolicy.evaluate(new CliInput(2000, 3000, 12, 10, 12, 0.30, 10000, false));
        assertThat(d.outcome()).isEqualTo(CliOutcome.APPROVED);
        assertThat(d.newLimit()).isEqualTo(3000);
    }

    @Test
    void utilizationAbove90IsDeclined() {
        CliDecision d = CliPolicy.evaluate(new CliInput(2000, 3000, 12, 12, 12, 0.95, 10000, false));
        assertThat(d.outcome()).isEqualTo(CliOutcome.DECLINED);
        assertThat(d.reasons()).containsExactly("Utilization above 90%");
    }

    @Test
    void everyFailedGateIsCollectedInOrder() {
        CliDecision d = CliPolicy.evaluate(new CliInput(2000, 3000, 3, 0, 12, 0.99, 10000, true));
        assertThat(d.outcome()).isEqualTo(CliOutcome.DECLINED);
        assertThat(d.newLimit()).isEqualTo(2000);
        assertThat(d.reasons()).containsExactly(
                "Account is past due",
                "Account open less than 6 months",
                "Fewer than 10 of 12 payments on time",
                "Utilization above 90%");
    }

    @Test
    void requestWithinMaxAllowedIsApprovedAsRequested() {
        // maxAllowed = floor(min(10000, 2*2000, 25000)/100)*100 = 4000; requested 3000 <= 4000.
        CliDecision d = CliPolicy.evaluate(new CliInput(2000, 3000, 12, 12, 12, 0.30, 10000, false));
        assertThat(d.outcome()).isEqualTo(CliOutcome.APPROVED);
        assertThat(d.newLimit()).isEqualTo(3000);
        assertThat(d.reasons()).isEmpty();
    }

    @Test
    void requestAboveMaxAllowedButRoomToGrowIsCountered() {
        // maxAllowed = 4000 (2*2000), requested 5000 > 4000, and 4000 > current 2000.
        CliDecision d = CliPolicy.evaluate(new CliInput(2000, 5000, 12, 12, 12, 0.30, 10000, false));
        assertThat(d.outcome()).isEqualTo(CliOutcome.COUNTER_OFFER);
        assertThat(d.newLimit()).isEqualTo(4000);
        assertThat(d.reasons()).containsExactly("Requested limit exceeds what the account qualifies for");
    }

    @Test
    void requestAboveMaxAllowedWithNoRoomIsDeclined() {
        // maxAllowed = min(3000, 2*4000, 25000) = 3000 <= current 4000; no room to grow.
        CliDecision d = CliPolicy.evaluate(new CliInput(4000, 5000, 12, 12, 12, 0.30, 3000, false));
        assertThat(d.outcome()).isEqualTo(CliOutcome.DECLINED);
        assertThat(d.newLimit()).isEqualTo(4000);
        assertThat(d.reasons()).containsExactly("Not eligible for a higher limit");
    }

    @Test
    void approvedLimitIsCappedAt25000() {
        // atpMax and 2*current are large, so the $25,000 ceiling binds.
        CliDecision d = CliPolicy.evaluate(new CliInput(20000, 25000, 24, 12, 12, 0.10, 90000, false));
        assertThat(d.outcome()).isEqualTo(CliOutcome.APPROVED);
        assertThat(d.newLimit()).isEqualTo(25000);
        assertThat(d.reasons()).isEqualTo(List.of());
    }
}
