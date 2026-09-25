package com.parallax.engine.config;

import com.parallax.engine.model.BandLimit;
import com.parallax.engine.model.RuleConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class RuleConfigValidatorTest {

    @Test
    void v1_3IsValid() {
        assertThat(RuleConfigValidator.validate(RuleConfigs.v1_3())).isEmpty();
    }

    @Test
    void v1_2IsValid() {
        assertThat(RuleConfigValidator.validate(RuleConfigs.v1_2())).isEmpty();
    }

    @Test
    void tradelinePtsWithNoOrderingIsAccepted() {
        // [20,55,70,60] deliberately dips (11+ below 5–10). No ordering rule applies to tradelinePts.
        RuleConfig c = withTradelinePts(base(), List.of(20, 55, 70, 60));
        assertThat(RuleConfigValidator.validate(c))
                .noneMatch(m -> m.contains("tradelinePts"));
    }

    @Test
    void cutoffOrderMessageMatchesRequiredWording() {
        RuleConfig c = withCutoffs(base(), 680, 700);
        assertThat(RuleConfigValidator.validate(c))
                .containsExactly("Cutoffs out of order: referCutoff (700) must be below approveCutoff (680)");
    }

    @Test
    void sumOfMaximaMessageMatchesRequiredWording() {
        RuleConfig c = withIncomePts(base(), List.of(10, 25, 40, 60)); // max 60 → sum 560
        assertThat(RuleConfigValidator.validate(c))
                .containsExactly("Sum of attribute maxima must be 550 (got 560)");
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("oneViolationCases")
    void eachRuleProducesExactlyOneMessageNamingTheField(String name, RuleConfig config, String mustContain) {
        List<String> messages = RuleConfigValidator.validate(config);
        assertThat(messages).as("case %s -> %s", name, messages).hasSize(1);
        assertThat(messages.get(0)).contains(mustContain);
    }

    static Stream<Arguments> oneViolationCases() {
        RuleConfig b = base();
        return Stream.of(
                Arguments.of("approveCutoff out of range", withApproveCutoff(b, 900), "approveCutoff"),
                Arguments.of("referCutoff out of range", withReferCutoff(b, 250), "referCutoff"),
                Arguments.of("cutoffs out of order", withCutoffs(b, 680, 700), "referCutoff"),
                Arguments.of("atpShare out of range", withAtpShare(b, 0.05), "atpShare"),
                Arguments.of("minPayPct out of range", withMinPayPct(b, 0.10), "minPayPct"),
                Arguments.of("livingCost negative", withLivingCost(b, -1), "livingCost"),
                Arguments.of("minLimit too low", withMinLimit(b, 50), "minLimit"),
                Arguments.of("bandLimits not descending by score",
                        withBandLimits(b, List.of(bl(800, 12000), bl(810, 7500), bl(720, 4000), bl(680, 2000), bl(0, 1000))),
                        "bandLimits"),
                Arguments.of("bandLimits not descending by limit",
                        withBandLimits(b, List.of(bl(800, 12000), bl(760, 13000), bl(720, 4000), bl(680, 2000), bl(0, 1000))),
                        "bandLimits"),
                Arguments.of("bandLimits last minScore not zero",
                        withBandLimits(b, List.of(bl(800, 12000), bl(760, 7500), bl(720, 4000), bl(680, 2000), bl(10, 1000))),
                        "bandLimits"),
                Arguments.of("bandLimits top limit over 25000",
                        withBandLimits(b, List.of(bl(800, 26000), bl(760, 7500), bl(720, 4000), bl(680, 2000), bl(0, 1000))),
                        "bandLimits"),
                Arguments.of("utilPts wrong length", withUtilPts(b, List.of(130, 115, 85, 45)), "utilPts"),
                Arguments.of("inqPts wrong length", withInqPts(b, List.of(90, 70, 35)), "inqPts"),
                Arguments.of("delqPts wrong length", withDelqPts(b, List.of(140, 60)), "delqPts"),
                Arguments.of("tradelinePts wrong length", withTradelinePts(b, List.of(20, 55, 70)), "tradelinePts"),
                Arguments.of("fileAgePts wrong length", withFileAgePts(b, List.of(15, 60, 70)), "fileAgePts"),
                Arguments.of("incomePts wrong length", withIncomePts(b, List.of(10, 40, 50)), "incomePts"),
                Arguments.of("utilPts increasing", withUtilPts(b, List.of(130, 115, 85, 45, 50)), "utilPts"),
                Arguments.of("inqPts increasing", withInqPts(b, List.of(90, 70, 35, 40)), "inqPts"),
                Arguments.of("delqPts increasing", withDelqPts(b, List.of(140, 60, 70)), "delqPts"),
                Arguments.of("fileAgePts decreasing", withFileAgePts(b, List.of(70, 40, 60, 70)), "fileAgePts"),
                Arguments.of("incomePts decreasing", withIncomePts(b, List.of(10, 25, 50, 40)), "incomePts"),
                Arguments.of("negative point", withUtilPts(b, List.of(130, 115, 85, 45, -10)), "utilPts"),
                Arguments.of("sum of maxima not 550", withIncomePts(b, List.of(10, 25, 40, 60)), "maxima"),
                Arguments.of("ccf out of range", withCcf(b, 1.5), "ccf"),
                Arguments.of("lgd out of range", withLgd(b, -0.1), "lgd"));
    }

    // --- helpers: start from valid v1.3 and change exactly one field ---

    private static RuleConfig base() {
        return RuleConfigs.v1_3();
    }

    private static BandLimit bl(int minScore, int limit) {
        return new BandLimit(minScore, limit);
    }

    private static RuleConfig withApproveCutoff(RuleConfig c, int v) {
        return new RuleConfig(v, c.referCutoff(), c.minPayPct(), c.atpShare(), c.livingCost(), c.minLimit(),
                c.bandLimits(), c.utilPts(), c.inqPts(), c.delqPts(), c.tradelinePts(), c.fileAgePts(), c.incomePts(),
                c.ccf(), c.lgd());
    }

    private static RuleConfig withReferCutoff(RuleConfig c, int v) {
        return new RuleConfig(c.approveCutoff(), v, c.minPayPct(), c.atpShare(), c.livingCost(), c.minLimit(),
                c.bandLimits(), c.utilPts(), c.inqPts(), c.delqPts(), c.tradelinePts(), c.fileAgePts(), c.incomePts(),
                c.ccf(), c.lgd());
    }

    private static RuleConfig withCutoffs(RuleConfig c, int approve, int refer) {
        return new RuleConfig(approve, refer, c.minPayPct(), c.atpShare(), c.livingCost(), c.minLimit(),
                c.bandLimits(), c.utilPts(), c.inqPts(), c.delqPts(), c.tradelinePts(), c.fileAgePts(), c.incomePts(),
                c.ccf(), c.lgd());
    }

    private static RuleConfig withAtpShare(RuleConfig c, double v) {
        return new RuleConfig(c.approveCutoff(), c.referCutoff(), c.minPayPct(), v, c.livingCost(), c.minLimit(),
                c.bandLimits(), c.utilPts(), c.inqPts(), c.delqPts(), c.tradelinePts(), c.fileAgePts(), c.incomePts(),
                c.ccf(), c.lgd());
    }

    private static RuleConfig withMinPayPct(RuleConfig c, double v) {
        return new RuleConfig(c.approveCutoff(), c.referCutoff(), v, c.atpShare(), c.livingCost(), c.minLimit(),
                c.bandLimits(), c.utilPts(), c.inqPts(), c.delqPts(), c.tradelinePts(), c.fileAgePts(), c.incomePts(),
                c.ccf(), c.lgd());
    }

    private static RuleConfig withLivingCost(RuleConfig c, int v) {
        return new RuleConfig(c.approveCutoff(), c.referCutoff(), c.minPayPct(), c.atpShare(), v, c.minLimit(),
                c.bandLimits(), c.utilPts(), c.inqPts(), c.delqPts(), c.tradelinePts(), c.fileAgePts(), c.incomePts(),
                c.ccf(), c.lgd());
    }

    private static RuleConfig withMinLimit(RuleConfig c, int v) {
        return new RuleConfig(c.approveCutoff(), c.referCutoff(), c.minPayPct(), c.atpShare(), c.livingCost(), v,
                c.bandLimits(), c.utilPts(), c.inqPts(), c.delqPts(), c.tradelinePts(), c.fileAgePts(), c.incomePts(),
                c.ccf(), c.lgd());
    }

    private static RuleConfig withBandLimits(RuleConfig c, List<BandLimit> v) {
        return new RuleConfig(c.approveCutoff(), c.referCutoff(), c.minPayPct(), c.atpShare(), c.livingCost(), c.minLimit(),
                v, c.utilPts(), c.inqPts(), c.delqPts(), c.tradelinePts(), c.fileAgePts(), c.incomePts(),
                c.ccf(), c.lgd());
    }

    private static RuleConfig withUtilPts(RuleConfig c, List<Integer> v) {
        return new RuleConfig(c.approveCutoff(), c.referCutoff(), c.minPayPct(), c.atpShare(), c.livingCost(), c.minLimit(),
                c.bandLimits(), v, c.inqPts(), c.delqPts(), c.tradelinePts(), c.fileAgePts(), c.incomePts(),
                c.ccf(), c.lgd());
    }

    private static RuleConfig withInqPts(RuleConfig c, List<Integer> v) {
        return new RuleConfig(c.approveCutoff(), c.referCutoff(), c.minPayPct(), c.atpShare(), c.livingCost(), c.minLimit(),
                c.bandLimits(), c.utilPts(), v, c.delqPts(), c.tradelinePts(), c.fileAgePts(), c.incomePts(),
                c.ccf(), c.lgd());
    }

    private static RuleConfig withDelqPts(RuleConfig c, List<Integer> v) {
        return new RuleConfig(c.approveCutoff(), c.referCutoff(), c.minPayPct(), c.atpShare(), c.livingCost(), c.minLimit(),
                c.bandLimits(), c.utilPts(), c.inqPts(), v, c.tradelinePts(), c.fileAgePts(), c.incomePts(),
                c.ccf(), c.lgd());
    }

    private static RuleConfig withTradelinePts(RuleConfig c, List<Integer> v) {
        return new RuleConfig(c.approveCutoff(), c.referCutoff(), c.minPayPct(), c.atpShare(), c.livingCost(), c.minLimit(),
                c.bandLimits(), c.utilPts(), c.inqPts(), c.delqPts(), v, c.fileAgePts(), c.incomePts(),
                c.ccf(), c.lgd());
    }

    private static RuleConfig withFileAgePts(RuleConfig c, List<Integer> v) {
        return new RuleConfig(c.approveCutoff(), c.referCutoff(), c.minPayPct(), c.atpShare(), c.livingCost(), c.minLimit(),
                c.bandLimits(), c.utilPts(), c.inqPts(), c.delqPts(), c.tradelinePts(), v, c.incomePts(),
                c.ccf(), c.lgd());
    }

    private static RuleConfig withIncomePts(RuleConfig c, List<Integer> v) {
        return new RuleConfig(c.approveCutoff(), c.referCutoff(), c.minPayPct(), c.atpShare(), c.livingCost(), c.minLimit(),
                c.bandLimits(), c.utilPts(), c.inqPts(), c.delqPts(), c.tradelinePts(), c.fileAgePts(), v,
                c.ccf(), c.lgd());
    }

    private static RuleConfig withCcf(RuleConfig c, double v) {
        return new RuleConfig(c.approveCutoff(), c.referCutoff(), c.minPayPct(), c.atpShare(), c.livingCost(), c.minLimit(),
                c.bandLimits(), c.utilPts(), c.inqPts(), c.delqPts(), c.tradelinePts(), c.fileAgePts(), c.incomePts(),
                v, c.lgd());
    }

    private static RuleConfig withLgd(RuleConfig c, double v) {
        return new RuleConfig(c.approveCutoff(), c.referCutoff(), c.minPayPct(), c.atpShare(), c.livingCost(), c.minLimit(),
                c.bandLimits(), c.utilPts(), c.inqPts(), c.delqPts(), c.tradelinePts(), c.fileAgePts(), c.incomePts(),
                c.ccf(), v);
    }
}
