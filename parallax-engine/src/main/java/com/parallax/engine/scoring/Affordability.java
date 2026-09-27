package com.parallax.engine.scoring;

import com.parallax.engine.model.RuleConfig;

/**
 * The ability-to-pay maximum (SPEC §4): the largest credit line whose minimum payment the applicant's
 * residual monthly income can service. Pure and shared — the decision engine uses it during a decision,
 * and account-service uses it to size credit-line increases (SPEC §13), both under the LIVE config.
 *
 * <p>{@code residual = annualIncome / 12 − monthlyHousing − monthlyDebt − livingCost};
 * {@code atpMax = max(0, floor(residual × atpShare / minPayPct / 100) × 100)}.
 */
public final class Affordability {

    private Affordability() {
    }

    public static int atpMax(int annualIncome, int monthlyHousing, int monthlyDebt, RuleConfig c) {
        double residual = annualIncome / 12.0 - monthlyHousing - monthlyDebt - c.livingCost();
        return Math.max(0, (int) Math.floor(residual * c.atpShare() / c.minPayPct() / 100.0) * 100);
    }
}
