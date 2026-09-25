package com.parallax.application.domain;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static com.parallax.application.domain.ApplicationStatus.BUREAU_PULLED;
import static com.parallax.application.domain.ApplicationStatus.DECIDED;
import static com.parallax.application.domain.ApplicationStatus.ENGINE_FAILED_MANUAL;
import static com.parallax.application.domain.ApplicationStatus.RECEIVED;
import static com.parallax.application.domain.ApplicationStatus.REVIEWED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ApplicationStateMachineTest {

    @ParameterizedTest
    @CsvSource({
            "RECEIVED, BUREAU_PULLED",
            "RECEIVED, BUREAU_UNAVAILABLE",
            "BUREAU_PULLED, DECIDED",
            "BUREAU_PULLED, ENGINE_PENDING",
            "BUREAU_UNAVAILABLE, DECIDED",
            "BUREAU_UNAVAILABLE, REVIEWED",
            "ENGINE_PENDING, DECIDED",
            "ENGINE_PENDING, ENGINE_FAILED_MANUAL",
            "DECIDED, REVIEWED"
    })
    void legalTransitionsPass(ApplicationStatus from, ApplicationStatus to) {
        assertThat(ApplicationStateMachine.transition(from, to)).isEqualTo(to);
    }

    @Test
    void illegalTransitionsThrow() {
        assertThatThrownBy(() -> ApplicationStateMachine.transition(RECEIVED, DECIDED))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> ApplicationStateMachine.transition(DECIDED, BUREAU_PULLED))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> ApplicationStateMachine.transition(BUREAU_PULLED, REVIEWED))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void terminalStatesHaveNoOutgoingTransition() {
        assertThatThrownBy(() -> ApplicationStateMachine.transition(REVIEWED, DECIDED))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> ApplicationStateMachine.transition(ENGINE_FAILED_MANUAL, DECIDED))
                .isInstanceOf(IllegalStateException.class);
    }
}
