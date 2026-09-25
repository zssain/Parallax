package com.parallax.engine.model;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class UsdTest {

    @Test
    void formatsWholeDollarsWithHandRolledGrouping() {
        assertThat(Usd.format(0)).isEqualTo("$0");
        assertThat(Usd.format(999)).isEqualTo("$999");
        assertThat(Usd.format(1000)).isEqualTo("$1,000");
        assertThat(Usd.format(29200)).isEqualTo("$29,200");
        assertThat(Usd.format(1234567)).isEqualTo("$1,234,567");
        assertThat(Usd.format(-100)).isEqualTo("−$100");
    }
}
