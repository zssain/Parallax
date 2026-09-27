package com.parallax.engine.scoring;

import com.parallax.engine.model.BandLimit;
import com.parallax.engine.model.Decision;
import com.parallax.engine.model.EngineInput;
import com.parallax.engine.model.Outcome;
import com.parallax.engine.model.PolicyCheck;
import com.parallax.engine.model.ReasonCode;
import com.parallax.engine.model.RuleConfig;
import com.parallax.engine.model.ScorePart;
import com.parallax.engine.model.Usd;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * The pure decision engine (SPEC §4): {@code EngineInput} + {@code RuleConfig} → {@code Decision}.
 * Same input and config always produce the same output — no static mutable state, randomness,
 * time, I/O or logging. Steps run in the exact order fraud → policy → scorecard → outcome →
 * limit → reason codes; the score is always computed.
 */
public final class DecisionEngine {

    private DecisionEngine() {
    }

    public static Decision evaluate(EngineInput in, RuleConfig c) {
        // 1. Fraud flags, in order.
        List<ReasonCode> fraudFlags = new ArrayList<>();
        if (in.addressMismatch()) {
            fraudFlags.add(ReasonCode.F01);
        }
        if (in.ssnIssuanceYear() < in.birthYear()) {
            fraudFlags.add(ReasonCode.F02);
        }
        if (in.deceased()) {
            fraudFlags.add(ReasonCode.F03);
        }
        if (in.velocity24h() >= 3) {
            fraudFlags.add(ReasonCode.F04);
        }

        // 2. Ability-to-pay maximum (shared with account-service's CLI sizing, SPEC §4/§13).
        int atpMax = Affordability.atpMax(in.annualIncome(), in.monthlyHousing(), in.monthlyDebt(), c);

        // 3. Policy checks, always all four, in order P02, P03, P04, P01.
        List<PolicyCheck> policyChecks = new ArrayList<>();
        policyChecks.add(new PolicyCheck(
                ReasonCode.P02, "Legal capacity (18+)",
                in.age() >= 18, "Age " + in.age()));
        boolean p03Passed = in.age() >= 21 || in.independentIncome();
        String p03Detail = in.age() >= 21
                ? "Not applicable"
                : (in.independentIncome() ? "Independent income" : "None declared");
        policyChecks.add(new PolicyCheck(
                ReasonCode.P03, "Under 21: independent income (CARD Act)",
                p03Passed, p03Detail));
        policyChecks.add(new PolicyCheck(
                ReasonCode.P04, "Bureau inquiry consent",
                in.bureauConsent(), in.bureauConsent() ? "Given" : "Missing"));
        policyChecks.add(new PolicyCheck(
                ReasonCode.P01, "Ability to pay (Reg Z 1026.51)",
                atpMax >= c.minLimit(), "Max affordable limit " + Usd.format(atpMax)));

        // 4. Score parts, in attribute order. score = 300 + Σ points.
        List<ScorePart> scoreParts = new ArrayList<>();
        scoreParts.add(utilizationPart(in, c));
        scoreParts.add(inquiriesPart(in, c));
        scoreParts.add(delinquenciesPart(in, c));
        scoreParts.add(tradelinesPart(in, c));
        scoreParts.add(fileAgePart(in, c));
        scoreParts.add(incomePart(in, c));
        int score = 300;
        for (ScorePart part : scoreParts) {
            score += part.points();
        }

        // 5. Outcome.
        List<PolicyCheck> failed = new ArrayList<>();
        for (PolicyCheck check : policyChecks) {
            if (!check.passed()) {
                failed.add(check);
            }
        }
        Outcome outcome;
        if (!fraudFlags.isEmpty()) {
            outcome = Outcome.REFER;
        } else if (!failed.isEmpty()) {
            outcome = Outcome.DECLINED;
        } else if (score >= c.approveCutoff()) {
            outcome = Outcome.APPROVED;
        } else if (score >= c.referCutoff()) {
            outcome = Outcome.REFER;
        } else {
            outcome = Outcome.DECLINED;
        }

        // 6. Limit — only when APPROVED.
        int creditLimit = 0;
        if (outcome == Outcome.APPROVED) {
            creditLimit = Math.min(firstBandLimit(c, score), atpMax);
        }

        // 7. Reason codes — only when not APPROVED. Failed policies first (already in order),
        //    then losing score parts by points lost descending (stable, ties keep attribute order),
        //    capped at 4 total. Never F-codes or B01.
        List<ReasonCode> reasonCodes = new ArrayList<>();
        if (outcome != Outcome.APPROVED) {
            for (PolicyCheck check : failed) {
                reasonCodes.add(check.code());
            }
            List<ScorePart> losing = new ArrayList<>();
            for (ScorePart part : scoreParts) {
                if (part.pointsLost() > 0) {
                    losing.add(part);
                }
            }
            losing.sort(Comparator.comparingInt(ScorePart::pointsLost).reversed());
            for (ScorePart part : losing) {
                if (reasonCodes.size() >= 4) {
                    break;
                }
                reasonCodes.add(part.code());
            }
        }

        return new Decision(outcome, score, creditLimit, reasonCodes, fraudFlags, policyChecks, scoreParts, atpMax);
    }

    private static ScorePart utilizationPart(EngineInput in, RuleConfig c) {
        double u = in.revolvingUtilization();
        int i = u < 0.10 ? 0 : u < 0.30 ? 1 : u < 0.50 ? 2 : u < 0.75 ? 3 : 4;
        String[] bands = {"<10%", "10–29%", "30–49%", "50–74%", "75%+"};
        String value = Math.round(u * 100) + "%";
        return new ScorePart("Revolving utilization", value, bands[i],
                c.utilPts().get(i), max(c.utilPts()), ReasonCode.R31);
    }

    private static ScorePart inquiriesPart(EngineInput in, RuleConfig c) {
        int n = in.inquiries6m();
        int i = n == 0 ? 0 : n <= 2 ? 1 : n <= 4 ? 2 : 3;
        String[] bands = {"0", "1–2", "3–4", "5+"};
        return new ScorePart("Inquiries (6 mo)", Integer.toString(n), bands[i],
                c.inqPts().get(i), max(c.inqPts()), ReasonCode.R14);
    }

    private static ScorePart delinquenciesPart(EngineInput in, RuleConfig c) {
        int n = in.delinquencies24m();
        int i = n == 0 ? 0 : n == 1 ? 1 : 2;
        String[] bands = {"0", "1", "2+"};
        return new ScorePart("Delinquencies (24 mo)", Integer.toString(n), bands[i],
                c.delqPts().get(i), max(c.delqPts()), ReasonCode.R22);
    }

    private static ScorePart tradelinesPart(EngineInput in, RuleConfig c) {
        int n = in.openTradelines();
        int i = n <= 1 ? 0 : n <= 4 ? 1 : n <= 10 ? 2 : 3;
        String[] bands = {"0–1", "2–4", "5–10", "11+"};
        return new ScorePart("Open tradelines", Integer.toString(n), bands[i],
                c.tradelinePts().get(i), max(c.tradelinePts()), ReasonCode.R07);
    }

    private static ScorePart fileAgePart(EngineInput in, RuleConfig c) {
        int n = in.fileAgeMonths();
        int i = n < 24 ? 0 : n < 60 ? 1 : n < 120 ? 2 : 3;
        String[] bands = {"<2 yr", "2–4 yr", "5–9 yr", "10+ yr"};
        return new ScorePart("Credit file age", n + " mo", bands[i],
                c.fileAgePts().get(i), max(c.fileAgePts()), ReasonCode.R05);
    }

    private static ScorePart incomePart(EngineInput in, RuleConfig c) {
        int n = in.annualIncome();
        int i = n < 25000 ? 0 : n < 50000 ? 1 : n < 100000 ? 2 : 3;
        String[] bands = {"<$25k", "$25–50k", "$50–100k", "$100k+"};
        return new ScorePart("Income band", Usd.format(n), bands[i],
                c.incomePts().get(i), max(c.incomePts()), ReasonCode.R33);
    }

    private static int firstBandLimit(RuleConfig c, int score) {
        for (BandLimit band : c.bandLimits()) {
            if (score >= band.minScore()) {
                return band.limit();
            }
        }
        return 0;
    }

    private static int max(List<Integer> points) {
        int max = points.get(0);
        for (int p : points) {
            if (p > max) {
                max = p;
            }
        }
        return max;
    }
}
