package com.parallax.engine.model;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EngineInputTest {

    private static EngineInput valid(double utilization, int annualIncome) {
        return new EngineInput(
                31, 1995, annualIncome, 1300, 520, utilization,
                5, 2, 4, 30, true, true, false, 1996, false, 1, PullType.HARD);
    }

    @Test
    void validInputConstructs() {
        EngineInput input = valid(0.82, 38000);
        assertThat(input.revolvingUtilization()).isEqualTo(0.82);
        assertThat(input.pullType()).isEqualTo(PullType.HARD);
        assertThat(input.annualIncome()).isEqualTo(38000);
    }

    @Test
    void rejectsUtilizationAboveOne() {
        assertThatThrownBy(() -> valid(1.2, 38000))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("revolvingUtilization");
    }

    @Test
    void rejectsNegativeUtilization() {
        assertThatThrownBy(() -> valid(-0.1, 38000))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("revolvingUtilization");
    }

    @Test
    void rejectsNegativeIncome() {
        assertThatThrownBy(() -> valid(0.82, -1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("annualIncome");
    }

    @Test
    void rejectsNaNUtilization() {
        assertThatThrownBy(() -> valid(Double.NaN, 38000))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("revolvingUtilization");
    }

    @Test
    void rejectsNullPullType() {
        assertThatThrownBy(() -> new EngineInput(
                31, 1995, 38000, 1300, 520, 0.82,
                5, 2, 4, 30, true, true, false, 1996, false, 1, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("pullType");
    }

    @Test
    void boundaryUtilizationValuesAreAccepted() {
        assertThatCode(() -> valid(0.0, 38000)).doesNotThrowAnyException();
        assertThatCode(() -> valid(1.0, 38000)).doesNotThrowAnyException();
    }
}
