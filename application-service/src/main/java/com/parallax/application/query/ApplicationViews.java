package com.parallax.application.query;

import java.time.Instant;
import java.util.List;

/** Response DTOs for the applications list and detail (SPEC §15). */
public final class ApplicationViews {

    private ApplicationViews() {
    }

    public record ListResponse(List<ListItem> items, int page, int size, long total, Counts counts) {
    }

    public record ListItem(String applicationId, String displayName, String product, Integer score,
                           String outcome, Integer creditLimit, String ruleVersion, String currentKind,
                           Instant recordedAt) {
    }

    public record Counts(long ALL, long APPROVED, long REFER, long DECLINED) {
    }

    public record Detail(String applicationId, String displayName, String product, String status,
                         Instant createdAt, String ssnMasked, String ssnEncPreview, String address,
                         List<String> untrustedTextFields, Current current, Base base, Breakdown breakdown,
                         Bureau bureau, List<TrailItem> trail, Object shadow) {
    }

    public record Current(long seq, String kind, String outcome, Integer creditLimit, Override override) {
    }

    public record Override(String by, String code, String codeDescription, String note) {
    }

    public record Base(long seq, String kind, String ruleVersion, String scorecardVersion, String engineVersion,
                       String outcome, Integer score, Integer creditLimit, List<Reason> reasonCodes,
                       List<Fraud> fraudFlags, Integer atpMax, Object engineInput, Instant createdAt) {
    }

    public record Reason(String code, String description, boolean applicantFacing) {
    }

    public record Fraud(String code, String description) {
    }

    public record Breakdown(List<PolicyCheck> policyChecks, List<ScorePart> scoreParts, int approveCutoff,
                            int referCutoff, Integer bandLimit) {
    }

    public record PolicyCheck(String code, String name, boolean passed, String detail) {
    }

    public record ScorePart(String attribute, String value, String band, int points, int maxPoints,
                            int pointsLost, String code) {
    }

    public record Bureau(String pullId, String pullType, boolean reused, String rawXml) {
    }

    public record TrailItem(long seq, String kind, String outcome, String ruleVersion, Instant createdAt,
                            String prevHash, String hash, Override override) {
    }

    /** The shadow evaluation of this decision, if a version was scoring live traffic (SPEC §10). */
    public record Shadow(String version, String outcome, Integer score, Integer creditLimit, boolean agrees) {
    }
}
