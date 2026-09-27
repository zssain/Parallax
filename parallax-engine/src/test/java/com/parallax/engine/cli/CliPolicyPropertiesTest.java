package com.parallax.engine.cli;

import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Combinators;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;

import static org.assertj.core.api.Assertions.assertThat;

/** Property-based invariants of the CLI policy (jqwik, 1,000 tries each). */
class CliPolicyPropertiesTest {

    @Provide
    Arbitrary<CliInput> inputs() {
        Arbitrary<Integer> currentLimit = Arbitraries.integers().between(0, 30_000);
        Arbitrary<Integer> requestedLimit = Arbitraries.integers().between(0, 60_000);
        Arbitrary<Integer> closed = Arbitraries.integers().between(0, 60);
        Arbitrary<Integer> due = Arbitraries.integers().between(0, 12);
        Arbitrary<Integer> onTimeRaw = Arbitraries.integers().between(0, 12);
        Arbitrary<Integer> utilMilli = Arbitraries.integers().between(0, 2_000);
        Arbitrary<Integer> atpMax = Arbitraries.integers().between(0, 60_000);
        Arbitrary<Boolean> delinquent = Arbitraries.of(true, false);

        return Combinators.combine(currentLimit, requestedLimit, closed, due, onTimeRaw, utilMilli, atpMax, delinquent)
                .as((cur, req, cl, d, onTime, um, atp, del) ->
                        new CliInput(cur, req, cl, Math.min(onTime, d), d, um / 1000.0, atp, del));
    }

    @Property(tries = 1000)
    void newLimitNeverExceedsAffordabilityOrTwiceCurrent(@ForAll("inputs") CliInput in) {
        CliDecision decision = CliPolicy.evaluate(in);
        assertThat(decision.newLimit()).isLessThanOrEqualTo(Math.max(in.currentLimit(), in.atpMax()));
        assertThat(decision.newLimit()).isLessThanOrEqualTo(Math.max(in.currentLimit(), 2 * in.currentLimit()));
    }

    @Property(tries = 1000)
    void declinedKeepsTheCurrentLimit(@ForAll("inputs") CliInput in) {
        CliDecision decision = CliPolicy.evaluate(in);
        if (decision.outcome() == CliOutcome.DECLINED) {
            assertThat(decision.newLimit()).isEqualTo(in.currentLimit());
        }
    }

    @Property(tries = 1000)
    void approvedGrantsExactlyWhatWasRequested(@ForAll("inputs") CliInput in) {
        CliDecision decision = CliPolicy.evaluate(in);
        if (decision.outcome() == CliOutcome.APPROVED) {
            assertThat(decision.newLimit()).isEqualTo(in.requestedLimit());
        }
    }

    @Property(tries = 1000)
    void isDeterministic(@ForAll("inputs") CliInput in) {
        CliDecision a = CliPolicy.evaluate(in);
        CliDecision b = CliPolicy.evaluate(in);
        assertThat(a).isEqualTo(b);
    }
}
