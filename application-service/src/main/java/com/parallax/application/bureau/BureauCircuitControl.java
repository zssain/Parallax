package com.parallax.application.bureau;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.springframework.stereotype.Component;

/** Inspect and drive the bureau circuit breaker (SPEC §3; used by Prompt 11's dev endpoint). */
@Component
public class BureauCircuitControl {

    private final CircuitBreaker breaker;

    public BureauCircuitControl(CircuitBreakerRegistry registry) {
        this.breaker = registry.circuitBreaker("bureau");
    }

    /** CLOSED, OPEN or HALF_OPEN. */
    public String state() {
        return breaker.getState().name();
    }

    public boolean isOpen() {
        return breaker.getState() == CircuitBreaker.State.OPEN
                || breaker.getState() == CircuitBreaker.State.FORCED_OPEN;
    }

    public void forceOpen() {
        breaker.transitionToOpenState();
    }

    public void close() {
        breaker.transitionToClosedState();
    }
}
