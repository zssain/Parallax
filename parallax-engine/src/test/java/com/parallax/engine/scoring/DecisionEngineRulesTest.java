package com.parallax.engine.scoring;

import com.parallax.engine.config.RuleConfigs;
import com.parallax.engine.model.Decision;
import com.parallax.engine.model.Outcome;
import com.parallax.engine.model.RuleConfig;
import org.junit.jupiter.api.Test;

import static com.parallax.engine.model.ReasonCode.F01;
import static com.parallax.engine.model.ReasonCode.F02;
import static com.parallax.engine.model.ReasonCode.F03;
import static com.parallax.engine.model.ReasonCode.F04;
import static com.parallax.engine.model.ReasonCode.P02;
import static com.parallax.engine.model.ReasonCode.P03;
import static com.parallax.engine.model.ReasonCode.P04;
import static com.parallax.engine.model.ReasonCode.R22;
import static com.parallax.engine.model.ReasonCode.R31;
import static org.assertj.core.api.Assertions.assertThat;

class DecisionEngineRulesTest {

    private static final RuleConfig C = RuleConfigs.v1_3();

    @Test
    void f02WhenSsnIssuedBeforeBirth() {
        Decision d = DecisionEngine.evaluate(TestInputs.builder().age(30).ssnYear(2026 - 30 - 3).build(), C);
        assertThat(d.fraudFlags()).contains(F02);
        assertThat(d.outcome()).isEqualTo(Outcome.REFER);
    }

    @Test
    void f03WhenDeceased() {
        Decision d = DecisionEngine.evaluate(TestInputs.builder().dead(true).build(), C);
        assertThat(d.fraudFlags()).contains(F03);
        assertThat(d.outcome()).isEqualTo(Outcome.REFER);
    }

    @Test
    void f01WhenAddressMismatch() {
        Decision d = DecisionEngine.evaluate(TestInputs.builder().addr(true).build(), C);
        assertThat(d.fraudFlags()).contains(F01);
        assertThat(d.outcome()).isEqualTo(Outcome.REFER);
    }

    @Test
    void allFourFraudFlagsInOrder() {
        Decision d = DecisionEngine.evaluate(TestInputs.builder()
                .age(30).addr(true).ssnYear(2026 - 30 - 3).dead(true).velocity(3).build(), C);
        assertThat(d.fraudFlags()).containsExactly(F01, F02, F03, F04);
        assertThat(d.outcome()).isEqualTo(Outcome.REFER);
    }

    @Test
    void fraudBeatsPolicy() {
        // Under-21 without independent income (would DECLINE) plus a fraud flag → REFER, reasons still start with P03.
        Decision d = DecisionEngine.evaluate(TestInputs.builder()
                .age(20).indep(false).addr(true).income(28000).housing(600).debt(50)
                .util(0.20).inq(1).delq(0).trades(1).file(10).build(), C);
        assertThat(d.outcome()).isEqualTo(Outcome.REFER);
        assertThat(d.fraudFlags()).contains(F01);
        assertThat(d.reasonCodes().get(0)).isEqualTo(P03);
    }

    @Test
    void p04WhenConsentMissing() {
        Decision d = DecisionEngine.evaluate(TestInputs.builder().age(30).consent(false).build(), C);
        assertThat(d.outcome()).isEqualTo(Outcome.DECLINED);
        assertThat(d.reasonCodes()).contains(P04);
    }

    @Test
    void p02WhenUnderage() {
        Decision d = DecisionEngine.evaluate(TestInputs.builder().age(17).build(), C);
        assertThat(d.outcome()).isEqualTo(Outcome.DECLINED);
        assertThat(d.reasonCodes().get(0)).isEqualTo(P02);
    }

    @Test
    void utilizationBandEdges() {
        assertThat(utilBand(0.0999)).isEqualTo("<10%");
        assertThat(utilBand(0.10)).isEqualTo("10–29%");
        assertThat(utilBand(0.2999)).isEqualTo("10–29%");
        assertThat(utilBand(0.30)).isEqualTo("30–49%");
        assertThat(utilBand(0.4999)).isEqualTo("30–49%");
        assertThat(utilBand(0.50)).isEqualTo("50–74%");
        assertThat(utilBand(0.7499)).isEqualTo("50–74%");
        assertThat(utilBand(0.75)).isEqualTo("75%+");
    }

    @Test
    void incomeBandEdges() {
        assertThat(incomeBand(24999)).isEqualTo("<$25k");
        assertThat(incomeBand(25000)).isEqualTo("$25–50k");
        assertThat(incomeBand(49999)).isEqualTo("$25–50k");
        assertThat(incomeBand(50000)).isEqualTo("$50–100k");
        assertThat(incomeBand(99999)).isEqualTo("$50–100k");
        assertThat(incomeBand(100000)).isEqualTo("$100k+");
    }

    @Test
    void fileAgeBandEdges() {
        assertThat(fileBand(23)).isEqualTo("<2 yr");
        assertThat(fileBand(24)).isEqualTo("2–4 yr");
        assertThat(fileBand(59)).isEqualTo("2–4 yr");
        assertThat(fileBand(60)).isEqualTo("5–9 yr");
        assertThat(fileBand(119)).isEqualTo("5–9 yr");
        assertThat(fileBand(120)).isEqualTo("10+ yr");
    }

    @Test
    void tradelineBandEdges() {
        assertThat(tradesBand(1)).isEqualTo("0–1");
        assertThat(tradesBand(2)).isEqualTo("2–4");
        assertThat(tradesBand(4)).isEqualTo("2–4");
        assertThat(tradesBand(5)).isEqualTo("5–10");
        assertThat(tradesBand(10)).isEqualTo("5–10");
        assertThat(tradesBand(11)).isEqualTo("11+");
    }

    @Test
    void limitCappedByAtp() {
        // Score 830 (band limit 12000) but atpMax 3000 → assigned 3000.
        Decision d = DecisionEngine.evaluate(TestInputs.builder()
                .age(30).income(50000).housing(2705).debt(0)
                .util(0.08).inq(0).delq(0).trades(12).file(156).build(), C);
        assertThat(d.outcome()).isEqualTo(Outcome.APPROVED);
        assertThat(d.atpMax()).isEqualTo(3000);
        assertThat(d.creditLimit()).isEqualTo(3000);
    }

    @Test
    void v12VersusV13OnNearPrime() {
        var in = TestInputs.builder()
                .age(30).income(45000).housing(1200).debt(300)
                .util(0.55).inq(3).delq(0).trades(5).file(40).build();
        Decision v13 = DecisionEngine.evaluate(in, RuleConfigs.v1_3());
        Decision v12 = DecisionEngine.evaluate(in, RuleConfigs.v1_2());
        assertThat(v13.score()).isEqualTo(655);
        assertThat(v12.score()).isEqualTo(660);
        assertThat(v13.outcome()).isEqualTo(Outcome.REFER);
        assertThat(v12.outcome()).isEqualTo(Outcome.REFER);
    }

    @Test
    void reasonsCappedAtFourWithPoliciesFirst() {
        // Two failed policies (P03, P04) and five losing attributes → exactly four reasons, policies first.
        Decision d = DecisionEngine.evaluate(TestInputs.builder()
                .age(20).indep(false).consent(false)
                .income(40000).housing(600).debt(50)
                .util(0.55).inq(3).delq(1).trades(8).file(40).build(), C);
        assertThat(d.reasonCodes()).containsExactly(P03, P04, R31, R22);
    }

    @Test
    void perfectScoreWithFraudRefersWithNoAdverseReasons() {
        // The one SPEC corner where reasonCodes is empty but the outcome is not APPROVED:
        // a perfect 850 score routed to REFER by a fraud flag has no failed policy and no lost points.
        Decision d = DecisionEngine.evaluate(TestInputs.builder()
                .age(30).income(120000).housing(500).debt(300)
                .util(0.05).inq(0).delq(0).trades(8).file(200).addr(true).build(), C);
        assertThat(d.score()).isEqualTo(850);
        assertThat(d.outcome()).isEqualTo(Outcome.REFER);
        assertThat(d.fraudFlags()).containsExactly(F01);
        assertThat(d.reasonCodes()).isEmpty();
    }

    private static String utilBand(double u) {
        return DecisionEngine.evaluate(TestInputs.builder().util(u).build(), C).scoreParts().get(0).band();
    }

    private static String incomeBand(int income) {
        return DecisionEngine.evaluate(TestInputs.builder().income(income).build(), C).scoreParts().get(5).band();
    }

    private static String fileBand(int fileAge) {
        return DecisionEngine.evaluate(TestInputs.builder().file(fileAge).build(), C).scoreParts().get(4).band();
    }

    private static String tradesBand(int trades) {
        return DecisionEngine.evaluate(TestInputs.builder().trades(trades).build(), C).scoreParts().get(3).band();
    }
}
