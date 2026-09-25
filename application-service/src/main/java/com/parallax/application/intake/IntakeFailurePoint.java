package com.parallax.application.intake;

import org.springframework.stereotype.Component;

/**
 * A no-op extension point invoked right after the idempotency key is inserted. Tests replace this
 * bean to inject a failure and assert the key is abandoned (deleted) when processing throws.
 */
@Component
public class IntakeFailurePoint {

    /** No-op in production; overridden in tests to throw. */
    public void afterKeyInsert() {
        // intentionally empty
    }
}
