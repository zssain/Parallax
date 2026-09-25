package com.parallax.application.intake;

/** What the controller should return: an HTTP status, a JSON body, and whether it is an idempotent replay. */
public record IntakeOutcome(int status, String jsonBody, boolean replay) {
}
