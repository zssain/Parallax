package com.parallax.application.decision;

/** The idempotency key owner (authenticated username + key) whose response is stored on commit. */
public record ClientKey(String clientId, String idemKey) {
}
