package com.parallax.application.ledger;

import com.parallax.engine.model.PullType;

/**
 * The partial engine input recorded for a bureau-unavailable (B01) row (SPEC §5): the fields known
 * without a bureau report, plus {@code bureau} which is always {@code "UNAVAILABLE"}. Serialized into
 * {@code engine_input} in place of a full {@link com.parallax.engine.model.EngineInput}.
 */
public record PartialEngineInput(
        int age,
        int birthYear,
        int annualIncome,
        int monthlyHousing,
        int monthlyDebt,
        boolean independentIncome,
        boolean bureauConsent,
        PullType pullType,
        String bureau) {

    public static PartialEngineInput unavailable(int age, int birthYear, int annualIncome, int monthlyHousing,
                                                 int monthlyDebt, boolean independentIncome, boolean bureauConsent,
                                                 PullType pullType) {
        return new PartialEngineInput(age, birthYear, annualIncome, monthlyHousing, monthlyDebt,
                independentIncome, bureauConsent, pullType, "UNAVAILABLE");
    }
}
