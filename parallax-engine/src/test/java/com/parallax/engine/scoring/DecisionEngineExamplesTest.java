package com.parallax.engine.scoring;

import com.parallax.engine.config.RuleConfigs;
import com.parallax.engine.model.Decision;
import com.parallax.engine.model.Outcome;
import com.parallax.engine.model.PolicyCheck;
import com.parallax.engine.model.ReasonCode;
import com.parallax.engine.model.RuleConfig;
import org.junit.jupiter.api.Test;

import static com.parallax.engine.model.ReasonCode.F04;
import static com.parallax.engine.model.ReasonCode.P01;
import static com.parallax.engine.model.ReasonCode.P03;
import static com.parallax.engine.model.ReasonCode.R05;
import static com.parallax.engine.model.ReasonCode.R07;
import static com.parallax.engine.model.ReasonCode.R14;
import static com.parallax.engine.model.ReasonCode.R22;
import static com.parallax.engine.model.ReasonCode.R31;
import static com.parallax.engine.model.ReasonCode.R33;
import static org.assertj.core.api.Assertions.assertThat;

/** The SPEC §4 worked examples — every column asserted, config v1.3. */
class DecisionEngineExamplesTest {

    private static final RuleConfig C = RuleConfigs.v1_3();

    private static String detail(Decision d, ReasonCode code) {
        return d.policyChecks().stream()
                .filter(p -> p.code() == code)
                .map(PolicyCheck::detail)
                .findFirst()
                .orElseThrow();
    }

    @Test
    void priya() {
        Decision d = DecisionEngine.evaluate(TestInputs.builder()
                .age(31).income(38000).housing(1300).debt(520)
                .util(0.82).inq(5).delq(2).trades(4).file(30).build(), C);

        assertThat(d.outcome()).isEqualTo(Outcome.DECLINED);
        assertThat(d.score()).isEqualTo(445);
        assertThat(d.atpMax()).isEqualTo(1700);
        assertThat(d.creditLimit()).isZero();
        assertThat(d.reasonCodes()).containsExactly(R22, R31, R14, R05);

        assertThat(detail(d, P01)).isEqualTo("Max affordable limit $1,700");

        assertThat(d.scoreParts().get(0).value()).isEqualTo("82%");
        assertThat(d.scoreParts().get(0).band()).isEqualTo("75%+");
        assertThat(d.scoreParts().get(1).value()).isEqualTo("5");
        assertThat(d.scoreParts().get(1).band()).isEqualTo("5+");
        assertThat(d.scoreParts().get(2).value()).isEqualTo("2");
        assertThat(d.scoreParts().get(2).band()).isEqualTo("2+");
        assertThat(d.scoreParts().get(3).value()).isEqualTo("4");
        assertThat(d.scoreParts().get(3).band()).isEqualTo("2–4");
        assertThat(d.scoreParts().get(4).value()).isEqualTo("30 mo");
        assertThat(d.scoreParts().get(4).band()).isEqualTo("2–4 yr");
        assertThat(d.scoreParts().get(5).value()).isEqualTo("$38,000");
        assertThat(d.scoreParts().get(5).band()).isEqualTo("$25–50k");
    }

    @Test
    void ishaan() {
        Decision d = DecisionEngine.evaluate(TestInputs.builder()
                .age(30).income(64000).housing(1350).debt(280)
                .util(0.08).inq(0).delq(0).trades(12).file(156).build(), C);

        assertThat(d.outcome()).isEqualTo(Outcome.APPROVED);
        assertThat(d.score()).isEqualTo(830);
        assertThat(d.atpMax()).isEqualTo(29200);
        assertThat(d.creditLimit()).isEqualTo(12000);
        assertThat(d.reasonCodes()).isEmpty();
    }

    @Test
    void nearPrime() {
        Decision d = DecisionEngine.evaluate(TestInputs.builder()
                .age(30).income(45000).housing(1200).debt(300)
                .util(0.55).inq(3).delq(0).trades(5).file(40).build(), C);

        assertThat(d.outcome()).isEqualTo(Outcome.REFER);
        assertThat(d.score()).isEqualTo(655);
        assertThat(d.atpMax()).isEqualTo(12200);
        assertThat(d.creditLimit()).isZero();
        assertThat(d.reasonCodes()).containsExactly(R31, R14, R05, R33);
    }

    @Test
    void under21() {
        Decision d = DecisionEngine.evaluate(TestInputs.builder()
                .age(20).indep(false).income(28000).housing(600).debt(50)
                .util(0.20).inq(1).delq(0).trades(1).file(10).build(), C);

        assertThat(d.outcome()).isEqualTo(Outcome.DECLINED);
        assertThat(d.score()).isEqualTo(685);
        assertThat(d.atpMax()).isEqualTo(5600);
        assertThat(d.creditLimit()).isZero();
        assertThat(d.reasonCodes()).containsExactly(P03, R05, R07, R33);
    }

    @Test
    void atpFail() {
        Decision d = DecisionEngine.evaluate(TestInputs.builder()
                .age(41).income(33000).housing(1150).debt(500)
                .util(0.82).inq(5).delq(2).trades(4).file(30).build(), C);

        assertThat(d.outcome()).isEqualTo(Outcome.DECLINED);
        assertThat(d.score()).isEqualTo(445);
        assertThat(d.atpMax()).isZero();
        assertThat(d.creditLimit()).isZero();
        assertThat(d.reasonCodes()).containsExactly(P01, R22, R31, R14);
    }

    @Test
    void velocity() {
        Decision d = DecisionEngine.evaluate(TestInputs.builder()
                .age(30).income(64000).housing(1350).debt(280)
                .util(0.08).inq(0).delq(0).trades(12).file(156)
                .velocity(3).build(), C);

        assertThat(d.outcome()).isEqualTo(Outcome.REFER);
        assertThat(d.score()).isEqualTo(830);
        assertThat(d.atpMax()).isEqualTo(29200);
        assertThat(d.creditLimit()).isZero();
        assertThat(d.reasonCodes()).containsExactly(R07, R33);
        assertThat(d.fraudFlags()).containsExactly(F04);
    }
}
