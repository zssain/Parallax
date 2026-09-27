package com.parallax.account.statement;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** Exact statement arithmetic in cents (SPEC §13). */
class StatementMathTest {

    @Test
    void interestAndMinimumDueToTheCent() {
        // balance 150000 cents, APR 2499 bps → interest 3124.
        long interest = StatementMath.interest(150000, 2499);
        assertThat(interest).isEqualTo(3124);

        // minimum = min(153124, max(2500, round(153124 * 1%) + 3124)) = min(153124, max(2500, 1531 + 3124)) = 4655.
        long postInterestBalance = 150000 + interest;
        assertThat(StatementMath.minimumDue(postInterestBalance, interest)).isEqualTo(4655);
    }

    @Test
    void noBalanceMeansNoInterestOrMinimum() {
        assertThat(StatementMath.interest(0, 2499)).isZero();
        assertThat(StatementMath.minimumDue(0, 0)).isZero();
    }

    @Test
    void smallBalanceHitsTheMinimumFloor() {
        long interest = StatementMath.interest(10000, 2499); // round(10000*2499/10000/12) = round(208.25) = 208
        assertThat(interest).isEqualTo(208);
        // max(2500, round(102.08)+208=310) = 2500; min(10208, 2500) = 2500.
        assertThat(StatementMath.minimumDue(10208, interest)).isEqualTo(2500);
    }
}
