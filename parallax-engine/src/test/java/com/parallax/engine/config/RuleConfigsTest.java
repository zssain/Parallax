package com.parallax.engine.config;

import com.parallax.engine.model.BandLimit;
import com.parallax.engine.model.RuleConfig;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RuleConfigsTest {

    @Test
    void v1_3EqualsSpec() {
        RuleConfig c = RuleConfigs.v1_3();
        assertThat(c.approveCutoff()).isEqualTo(680);
        assertThat(c.referCutoff()).isEqualTo(620);
        assertThat(c.minPayPct()).isEqualTo(0.03);
        assertThat(c.atpShare()).isEqualTo(0.35);
        assertThat(c.livingCost()).isEqualTo(1200);
        assertThat(c.minLimit()).isEqualTo(300);
        assertThat(c.bandLimits()).containsExactly(
                new BandLimit(800, 12000),
                new BandLimit(760, 7500),
                new BandLimit(720, 4000),
                new BandLimit(680, 2000),
                new BandLimit(0, 1000));
        assertThat(c.utilPts()).containsExactly(130, 115, 85, 45, 10);
        assertThat(c.inqPts()).containsExactly(90, 70, 35, 5);
        assertThat(c.delqPts()).containsExactly(140, 60, 10);
        assertThat(c.tradelinePts()).containsExactly(20, 55, 70, 60);
        assertThat(c.fileAgePts()).containsExactly(15, 40, 60, 70);
        assertThat(c.incomePts()).containsExactly(10, 25, 40, 50);
        assertThat(c.ccf()).isEqualTo(0.6);
        assertThat(c.lgd()).isEqualTo(0.9);
    }

    @Test
    void v1_2EqualsSpec() {
        RuleConfig c = RuleConfigs.v1_2();
        assertThat(c.approveCutoff()).isEqualTo(670);
        assertThat(c.referCutoff()).isEqualTo(620);
        assertThat(c.minPayPct()).isEqualTo(0.03);
        assertThat(c.atpShare()).isEqualTo(0.35);
        assertThat(c.livingCost()).isEqualTo(1200);
        assertThat(c.minLimit()).isEqualTo(300);
        assertThat(c.bandLimits()).containsExactly(
                new BandLimit(800, 12000),
                new BandLimit(760, 7500),
                new BandLimit(720, 4000),
                new BandLimit(680, 2000),
                new BandLimit(0, 1000));
        assertThat(c.utilPts()).containsExactly(130, 110, 80, 50, 15);
        assertThat(c.inqPts()).containsExactly(90, 70, 35, 5);
        assertThat(c.delqPts()).containsExactly(140, 60, 10);
        assertThat(c.tradelinePts()).containsExactly(20, 55, 70, 60);
        assertThat(c.fileAgePts()).containsExactly(15, 40, 60, 70);
        assertThat(c.incomePts()).containsExactly(10, 25, 40, 50);
        assertThat(c.ccf()).isEqualTo(0.6);
        assertThat(c.lgd()).isEqualTo(0.9);
    }
}
