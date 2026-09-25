package com.parallax.engine.scoring;

import com.parallax.engine.config.RuleConfigs;
import com.parallax.engine.model.Decision;
import com.parallax.engine.model.EngineInput;
import com.parallax.engine.model.Outcome;
import com.parallax.engine.model.PullType;
import com.parallax.engine.model.ReasonCode;
import com.parallax.engine.model.RuleConfig;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Combinators;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import net.jqwik.api.constraints.IntRange;

import static org.assertj.core.api.Assertions.assertThat;

/** Property-based invariants of the engine (jqwik, 1,000 tries each, config v1.3). */
class DecisionEnginePropertiesTest {

    private static final RuleConfig C = RuleConfigs.v1_3();

    @Provide
    Arbitrary<EngineInput> inputs() {
        Arbitrary<Integer> age = Arbitraries.integers().between(16, 80);
        Arbitrary<Integer> income = Arbitraries.integers().between(0, 300_000);
        Arbitrary<Integer> housing = Arbitraries.integers().between(0, 5_000);
        Arbitrary<Integer> debt = Arbitraries.integers().between(0, 5_000);
        Arbitrary<Integer> utilMilli = Arbitraries.integers().between(0, 1_000);
        Arbitrary<Integer> inq = Arbitraries.integers().between(0, 10);
        Arbitrary<Integer> delq = Arbitraries.integers().between(0, 5);
        Arbitrary<Integer> trades = Arbitraries.integers().between(0, 30);

        return Combinators.combine(age, income, housing, debt, utilMilli, inq, delq, trades)
                .as((a, inc, ho, de, um, iq, dq, tr) -> new int[]{a, inc, ho, de, um, iq, dq, tr})
                .flatMap(base -> Combinators.combine(
                        Arbitraries.integers().between(0, 400),
                        Arbitraries.of(true, false),
                        Arbitraries.of(true, false),
                        Arbitraries.of(true, false),
                        Arbitraries.of(true, false),
                        Arbitraries.integers().between(0, 5),
                        Arbitraries.integers().between(-5, 20)
                ).as((file, indep, consent, addr, dead, velocity, ssnOffset) -> {
                    int a = base[0];
                    int birthYear = 2026 - a;
                    return new EngineInput(a, birthYear, base[1], base[2], base[3], base[4] / 1000.0,
                            base[5], base[6], base[7], file, indep, consent, addr,
                            birthYear + ssnOffset, dead, velocity, PullType.HARD);
                }));
    }

    @Provide
    Arbitrary<Double> utils() {
        return Arbitraries.integers().between(0, 1_000).map(n -> n / 1000.0);
    }

    @Property(tries = 1000)
    void determinismProducesEqualDecisions(@ForAll("inputs") EngineInput in) {
        assertThat(DecisionEngine.evaluate(in, C)).isEqualTo(DecisionEngine.evaluate(in, C));
    }

    @Property(tries = 1000)
    void lowerUtilizationNeverLowersScore(@ForAll("inputs") EngineInput in, @ForAll("utils") double other) {
        double lo = Math.min(in.revolvingUtilization(), other);
        double hi = Math.max(in.revolvingUtilization(), other);
        int scoreLo = DecisionEngine.evaluate(withUtil(in, lo), C).score();
        int scoreHi = DecisionEngine.evaluate(withUtil(in, hi), C).score();
        assertThat(scoreLo).isGreaterThanOrEqualTo(scoreHi);
    }

    @Property(tries = 1000)
    void fewerDelinquenciesNeverLowersScore(@ForAll("inputs") EngineInput in,
                                            @ForAll @IntRange(min = 0, max = 5) int other) {
        int lo = Math.min(in.delinquencies24m(), other);
        int hi = Math.max(in.delinquencies24m(), other);
        int scoreLo = DecisionEngine.evaluate(withDelq(in, lo), C).score();
        int scoreHi = DecisionEngine.evaluate(withDelq(in, hi), C).score();
        assertThat(scoreLo).isGreaterThanOrEqualTo(scoreHi);
    }

    @Property(tries = 1000)
    void fewerInquiriesNeverLowersScore(@ForAll("inputs") EngineInput in,
                                        @ForAll @IntRange(min = 0, max = 10) int other) {
        int lo = Math.min(in.inquiries6m(), other);
        int hi = Math.max(in.inquiries6m(), other);
        int scoreLo = DecisionEngine.evaluate(withInq(in, lo), C).score();
        int scoreHi = DecisionEngine.evaluate(withInq(in, hi), C).score();
        assertThat(scoreLo).isGreaterThanOrEqualTo(scoreHi);
    }

    @Property(tries = 1000)
    void limitNeverExceedsAtpAndPositiveIffApproved(@ForAll("inputs") EngineInput in) {
        Decision d = DecisionEngine.evaluate(in, C);
        assertThat(d.creditLimit()).isLessThanOrEqualTo(d.atpMax());
        assertThat(d.creditLimit() > 0).isEqualTo(d.outcome() == Outcome.APPROVED);
    }

    @Property(tries = 1000)
    void reasonCodesAtMostFourAndTiedToApproval(@ForAll("inputs") EngineInput in) {
        Decision d = DecisionEngine.evaluate(in, C);
        assertThat(d.reasonCodes()).hasSizeLessThanOrEqualTo(4);
        if (d.outcome() == Outcome.APPROVED) {
            assertThat(d.reasonCodes()).isEmpty();
        }
        if (!d.reasonCodes().isEmpty()) {
            assertThat(d.outcome()).isNotEqualTo(Outcome.APPROVED);
        }
        // The only SPEC case of empty reasons without APPROVED: a perfect score referred by fraud.
        if (d.reasonCodes().isEmpty() && d.outcome() != Outcome.APPROVED) {
            assertThat(d.outcome()).isEqualTo(Outcome.REFER);
            assertThat(d.fraudFlags()).isNotEmpty();
        }
    }

    @Property(tries = 1000)
    void reasonCodesExcludeFraudAndB01WhileFraudFlagsAreOnlyFraud(@ForAll("inputs") EngineInput in) {
        Decision d = DecisionEngine.evaluate(in, C);
        assertThat(d.reasonCodes()).noneMatch(rc -> rc == ReasonCode.B01 || rc.isFraud());
        assertThat(d.fraudFlags()).allMatch(ReasonCode::isFraud);
    }

    @Property(tries = 1000)
    void scoreStaysWithin300To850(@ForAll("inputs") EngineInput in) {
        assertThat(DecisionEngine.evaluate(in, C).score()).isBetween(300, 850);
    }

    @Property(tries = 1000)
    void anyFraudFlagForcesRefer(@ForAll("inputs") EngineInput in) {
        Decision d = DecisionEngine.evaluate(in, C);
        if (!d.fraudFlags().isEmpty()) {
            assertThat(d.outcome()).isEqualTo(Outcome.REFER);
        }
    }

    @Property(tries = 1000)
    void ageNeverChangesScore(@ForAll("inputs") EngineInput in,
                              @ForAll @IntRange(min = 21, max = 80) int ageA,
                              @ForAll @IntRange(min = 21, max = 80) int ageB) {
        int scoreA = DecisionEngine.evaluate(withAge(in, ageA), C).score();
        int scoreB = DecisionEngine.evaluate(withAge(in, ageB), C).score();
        assertThat(scoreA).isEqualTo(scoreB);
    }

    private static EngineInput withUtil(EngineInput in, double u) {
        return new EngineInput(in.age(), in.birthYear(), in.annualIncome(), in.monthlyHousing(), in.monthlyDebt(),
                u, in.inquiries6m(), in.delinquencies24m(), in.openTradelines(), in.fileAgeMonths(),
                in.independentIncome(), in.bureauConsent(), in.addressMismatch(), in.ssnIssuanceYear(),
                in.deceased(), in.velocity24h(), in.pullType());
    }

    private static EngineInput withInq(EngineInput in, int inq) {
        return new EngineInput(in.age(), in.birthYear(), in.annualIncome(), in.monthlyHousing(), in.monthlyDebt(),
                in.revolvingUtilization(), inq, in.delinquencies24m(), in.openTradelines(), in.fileAgeMonths(),
                in.independentIncome(), in.bureauConsent(), in.addressMismatch(), in.ssnIssuanceYear(),
                in.deceased(), in.velocity24h(), in.pullType());
    }

    private static EngineInput withDelq(EngineInput in, int delq) {
        return new EngineInput(in.age(), in.birthYear(), in.annualIncome(), in.monthlyHousing(), in.monthlyDebt(),
                in.revolvingUtilization(), in.inquiries6m(), delq, in.openTradelines(), in.fileAgeMonths(),
                in.independentIncome(), in.bureauConsent(), in.addressMismatch(), in.ssnIssuanceYear(),
                in.deceased(), in.velocity24h(), in.pullType());
    }

    private static EngineInput withAge(EngineInput in, int age) {
        return new EngineInput(age, in.birthYear(), in.annualIncome(), in.monthlyHousing(), in.monthlyDebt(),
                in.revolvingUtilization(), in.inquiries6m(), in.delinquencies24m(), in.openTradelines(),
                in.fileAgeMonths(), in.independentIncome(), in.bureauConsent(), in.addressMismatch(),
                in.ssnIssuanceYear(), in.deceased(), in.velocity24h(), in.pullType());
    }
}
