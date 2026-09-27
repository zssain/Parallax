package com.parallax.account.collections;

import jakarta.validation.constraints.Pattern;

/** POST /api/v1/collections/{accountId}/actions body (SPEC §15). */
public record CollectionActionRequest(
        @Pattern(regexp = "PAYMENT_PLAN_OFFERED|CONTACTED", message = "must be PAYMENT_PLAN_OFFERED or CONTACTED")
        String type,
        String note) {
}
