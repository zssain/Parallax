package com.parallax.generator;

import com.parallax.engine.model.EngineInput;
import com.parallax.engine.model.PullType;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.SplittableRandom;

/**
 * Draws one synthetic applicant with a logistic PD model (SPEC §12 data, Prompt 12). Every random
 * draw ({@code r.nextDouble()}) happens in the exact order below, so a given seed and drift always
 * produce the same applicant. Tiers 0/1/2 are increasingly risky; {@code drift} shifts utilisation
 * and inquiries upward to simulate a deteriorating market.
 */
public final class ApplicantGenerator {

    private static final double[] UTIL_BASE = {0.30, 0.62, 0.95};
    private static final double[] INQ_BASE = {0.6, 1.5, 2.8};
    private static final double[] DELQ_THRESHOLD = {0.03, 0.14, 0.42};
    private static final int[] TRADELINE_BASE = {18, 11, 7};
    private static final int[] FILE_AGE_BASE = {260, 150, 90};
    private static final double[] INCOME_ADD = {30000, 8000, 0};
    private static final double[] DEBT_BASE = {500, 700, 1100};

    private ApplicantGenerator() {
    }

    public static GeneratedApplicant next(SplittableRandom r, double drift, int asOfYear) {
        double t = r.nextDouble();
        int tier = t < 0.32 ? 0 : t < 0.78 ? 1 : 2;

        double util = round3(clamp(r.nextDouble() * UTIL_BASE[tier] + drift * 0.20 * r.nextDouble(), 0, 0.99));

        int inq = (int) Math.min(8,
                Math.floor(-Math.log(1 - r.nextDouble() * 0.999) * INQ_BASE[tier] + drift * r.nextDouble() * 1.5));

        int delq;
        if (r.nextDouble() < DELQ_THRESHOLD[tier]) {
            delq = r.nextDouble() < 0.6 ? 1 : 2 + (int) Math.floor(r.nextDouble() * 2);
        } else {
            delq = 0;
        }

        int tradelines = 1 + (int) Math.floor(r.nextDouble() * TRADELINE_BASE[tier]);
        int fileAge = 6 + (int) Math.floor(r.nextDouble() * FILE_AGE_BASE[tier]);

        int income = (int) (Math.round((24000 + r.nextDouble() * r.nextDouble() * 150000 + INCOME_ADD[tier]) / 1000.0) * 1000);
        int housing = (int) (Math.round((500 + r.nextDouble() * 1700) / 50.0) * 50);
        int debt = (int) (Math.round(r.nextDouble() * DEBT_BASE[tier] / 10.0) * 10);

        int age = r.nextDouble() < 0.04
                ? 18 + (int) Math.floor(r.nextDouble() * 3)
                : 21 + (int) Math.floor(r.nextDouble() * 50);
        int birthYear = asOfYear - age;

        boolean independentIncome = r.nextDouble() < 0.55;
        boolean bureauConsent = true;
        boolean addressMismatch = r.nextDouble() < 0.008;

        int ssnIssuanceYear = birthYear + (r.nextDouble() < 0.002 ? -2 : 1 + (int) Math.floor(r.nextDouble() * 15));
        boolean deceased = r.nextDouble() < 0.001;
        int velocity24h = r.nextDouble() < 0.004 ? 3 : 1;
        PullType pullType = PullType.HARD;

        double dti = (housing + debt) * 12.0 / income;
        double z = -4.6 + 2.6 * util + 0.22 * Math.min(inq, 6) + 0.75 * Math.min(delq, 3)
                + 1.0 * Math.min(dti, 1.5) - 0.004 * Math.min(fileAge, 240);
        double pd = 1.0 / (1.0 + Math.exp(-z));
        boolean defaulted = r.nextDouble() < pd;

        EngineInput input = new EngineInput(age, birthYear, income, housing, debt, util, inq, delq,
                tradelines, fileAge, independentIncome, bureauConsent, addressMismatch, ssnIssuanceYear,
                deceased, velocity24h, pullType);
        return new GeneratedApplicant(input, pd, defaulted);
    }

    private static double clamp(double value, double lo, double hi) {
        return Math.max(lo, Math.min(hi, value));
    }

    private static double round3(double value) {
        return BigDecimal.valueOf(value).setScale(3, RoundingMode.HALF_UP).doubleValue();
    }
}
