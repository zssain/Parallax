package com.parallax.account.open;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;

/**
 * The ACCOUNT_OPEN_REQUESTED payload from application-service's outbox (SPEC §13, §15). ruleVersion and
 * ledgerSeq travel with the event for traceability; the account table does not store them.
 */
public record OpenAccountRequest(
        @NotBlank String applicationId,
        @NotBlank String displayName,
        @NotBlank String product,
        @PositiveOrZero int creditLimit,
        Integer annualIncome,
        Integer monthlyHousing,
        Integer monthlyDebt,
        String ruleVersion,
        Long ledgerSeq) {
}
