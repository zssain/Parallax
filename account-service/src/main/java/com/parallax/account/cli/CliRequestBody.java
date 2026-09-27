package com.parallax.account.cli;

import jakarta.validation.constraints.Positive;

/** POST /api/v1/accounts/{id}/cli-requests body (SPEC §15). */
public record CliRequestBody(@Positive int requestedLimit, boolean acceptCounterOffer) {
}
