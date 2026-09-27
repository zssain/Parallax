package com.parallax.account.statement;

import jakarta.validation.constraints.PositiveOrZero;

/** POST /api/v1/accounts/{id}/simulate-month body (SPEC §15, dev only). */
public record SimulateMonthRequest(
        @PositiveOrZero long purchasesCents,
        @PositiveOrZero long paymentCents,
        boolean payOnTime) {
}
