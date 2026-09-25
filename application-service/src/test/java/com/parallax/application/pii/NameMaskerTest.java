package com.parallax.application.pii;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class NameMaskerTest {

    @Test
    void masksEachWord() {
        assertThat(NameMasker.mask("Priya Sharma")).isEqualTo("P•••• S•••••");
    }

    @Test
    void shortWordsGetAtLeastTwoBullets() {
        assertThat(NameMasker.mask("Al Li")).isEqualTo("A•• L••");
    }
}
