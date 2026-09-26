package com.parallax.assistant.tools;

import com.fasterxml.jackson.databind.JsonNode;
import com.parallax.assistant.client.ParallaxClient;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The seven read-only tools the assistant may call (SPEC §12). Each returns a {@link ToolResult}:
 * data only, never a write. Applicant-authored text (the address) is flagged in untrustedTextFields
 * so the model treats it as data. Numbers come straight from application-service responses.
 */
@Component
public class ParallaxTools {

    private final ParallaxClient client;

    public ParallaxTools(ParallaxClient client) {
        this.client = client;
    }

    @Tool(description = "Get the current decision for an application: outcome, score, credit limit, rule "
            + "version, fraud flags, reason codes, masked name, product, status, any override, and address.")
    public ToolResult getDecision(
            @ToolParam(description = "Application public id, e.g. APP-1041") String applicationId) {
        JsonNode app = client.getApplication(applicationId);
        JsonNode current = app.get("current");
        JsonNode base = app.get("base");

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("applicationId", text(app, "applicationId"));
        data.put("outcome", text(current, "outcome"));
        data.put("score", intOrNull(node(base, "score")));
        data.put("creditLimit", intOrNull(node(current, "creditLimit")));
        data.put("ruleVersion", text(base, "ruleVersion"));
        data.put("fraudFlags", node(base, "fraudFlags"));
        data.put("reasonCodes", node(base, "reasonCodes"));
        data.put("maskedName", text(app, "displayName"));
        data.put("product", text(app, "product"));
        data.put("status", text(app, "status"));
        data.put("overrideSummary", overrideSummary(node(current, "override")));
        data.put("address", text(app, "address"));
        return ToolResult.of(List.of("address"), data);
    }

    @Tool(description = "List the reason codes for an application with their descriptions and points lost "
            + "(for R-codes), plus the approve and refer score cutoffs of the rule version used.")
    public ToolResult getReasonCodes(
            @ToolParam(description = "Application public id, e.g. APP-1041") String applicationId) {
        JsonNode app = client.getApplication(applicationId);
        JsonNode base = app.get("base");
        JsonNode breakdown = app.get("breakdown");

        Map<String, Integer> pointsLostByCode = new LinkedHashMap<>();
        if (breakdown != null && breakdown.hasNonNull("scoreParts")) {
            for (JsonNode part : breakdown.get("scoreParts")) {
                pointsLostByCode.put(text(part, "code"), intOrNull(node(part, "pointsLost")));
            }
        }

        List<Map<String, Object>> codes = new ArrayList<>();
        if (base != null && base.hasNonNull("reasonCodes")) {
            for (JsonNode reason : base.get("reasonCodes")) {
                String code = text(reason, "code");
                Map<String, Object> entry = new LinkedHashMap<>();
                entry.put("code", code);
                entry.put("description", text(reason, "description"));
                entry.put("pointsLost", code != null && code.startsWith("R") ? pointsLostByCode.get(code) : null);
                codes.add(entry);
            }
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("reasonCodes", codes);
        data.put("approveCutoff", breakdown == null ? null : intOrNull(node(breakdown, "approveCutoff")));
        data.put("referCutoff", breakdown == null ? null : intOrNull(node(breakdown, "referCutoff")));
        return ToolResult.of(data);
    }

    @Tool(description = "Return the existing replay impact report for a candidate rule version. "
            + "Read-only: it never starts a new replay; if none exists it reports that none is available.")
    public ToolResult runReplay(
            @ToolParam(description = "Candidate rule version, e.g. v1.4") String version) {
        JsonNode lookup = client.lookupReplay(version);
        if (lookup == null || !lookup.hasNonNull("jobId")) {
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("available", false);
            data.put("message", "No replay report exists for " + version + " — a strategist must run one first.");
            return ToolResult.of(data);
        }
        return ToolResult.of(replaySummary(client.getReplay(lookup.get("jobId").asText())));
    }

    @Tool(description = "Summarize a completed replay job: baseline and candidate approval rates, flips, "
            + "expected observed loss on both sides, outcomeUnknown, immature, and the segment that moved most.")
    public ToolResult getReplayReport(
            @ToolParam(description = "Replay job id, e.g. RJ-1A2B3C") String jobId) {
        return ToolResult.of(replaySummary(client.getReplay(jobId)));
    }

    @Tool(description = "Compare two rule versions field by field and list only the differing fields.")
    public ToolResult compareVersions(
            @ToolParam(description = "First version, e.g. v1.2") String a,
            @ToolParam(description = "Second version, e.g. v1.3") String b) {
        JsonNode compare = client.compareVersions(a, b);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("a", text(compare, "a"));
        data.put("b", text(compare, "b"));
        data.put("differences", node(compare, "differences"));
        return ToolResult.of(data);
    }

    @Tool(description = "Get override statistics: how many REFERs each score band had and how many were "
            + "overridden to APPROVE, with the override rate per band.")
    public ToolResult getOverrideStats() {
        JsonNode stats = client.getOverrideStats();
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("bands", node(stats, "bands"));
        return ToolResult.of(data);
    }

    @Tool(description = "Get the latest score-drift report: PSI, status (stable/watch/investigate), the "
            + "as-of date, and the score bin contributing most to the drift.")
    public ToolResult getDriftReport() {
        JsonNode drift = client.getDriftLatest();
        if (drift == null) {
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("available", false);
            data.put("message", "No drift report has been computed yet.");
            return ToolResult.of(data);
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("psi", drift.get("psi") == null ? null : drift.get("psi").asDouble());
        data.put("status", text(drift, "status"));
        data.put("asOf", text(drift, "asOf"));
        data.put("largestContributionBin", largestBin(drift.get("bins")));
        return ToolResult.of(data);
    }

    // --- summaries --------------------------------------------------------------------------------

    private Map<String, Object> replaySummary(JsonNode job) {
        Map<String, Object> data = new LinkedHashMap<>();
        JsonNode report = job == null ? null : job.get("report");
        if (report == null || report.isNull()) {
            data.put("available", false);
            data.put("message", "The replay job has no report yet (status "
                    + (job == null ? "unknown" : text(job, "status")) + ").");
            return data;
        }
        JsonNode baseline = report.get("baseline");
        JsonNode candidate = report.get("candidate");
        data.put("jobId", job.get("jobId") == null ? null : job.get("jobId").asText());
        data.put("baselineVersion", text(baseline, "version"));
        data.put("candidateVersion", text(candidate, "version"));
        data.put("baselineApprovalRate", doubleOrNull(node(baseline, "approvalRate")));
        data.put("candidateApprovalRate", doubleOrNull(node(candidate, "approvalRate")));
        data.put("flips", report.get("flips") == null ? null : report.get("flips").asLong());
        data.put("baselineExpectedLossObserved", doubleOrNull(node(baseline, "expectedLossObserved")));
        data.put("candidateExpectedLossObserved", doubleOrNull(node(candidate, "expectedLossObserved")));
        data.put("outcomeUnknown", node(report, "outcomeUnknown"));
        data.put("immature", node(report, "immature"));
        data.put("largestApprovalChangeSegment", largestApprovalChange(report.get("segments")));
        return data;
    }

    private Map<String, Object> largestApprovalChange(JsonNode segments) {
        if (segments == null || !segments.isArray() || segments.isEmpty()) {
            return null;
        }
        JsonNode best = null;
        long bestDelta = -1;
        for (JsonNode segment : segments) {
            long delta = Math.abs(segment.path("candidateApprovals").asLong()
                    - segment.path("baselineApprovals").asLong());
            if (delta > bestDelta) {
                bestDelta = delta;
                best = segment;
            }
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("band", text(best, "band"));
        data.put("baselineApprovals", best.path("baselineApprovals").asLong());
        data.put("candidateApprovals", best.path("candidateApprovals").asLong());
        return data;
    }

    private Map<String, Object> largestBin(JsonNode bins) {
        if (bins == null || !bins.isArray() || bins.isEmpty()) {
            return null;
        }
        JsonNode best = null;
        double bestContribution = -1;
        for (JsonNode bin : bins) {
            double contribution = Math.abs(bin.path("contribution").asDouble());
            if (contribution > bestContribution) {
                bestContribution = contribution;
                best = bin;
            }
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("from", best.path("from").asInt());
        data.put("to", best.path("to").asInt());
        data.put("contribution", best.path("contribution").asDouble());
        return data;
    }

    private String overrideSummary(JsonNode override) {
        if (override == null || override.isNull()) {
            return null;
        }
        return text(override, "by") + " · " + text(override, "code") + " " + text(override, "codeDescription");
    }

    // --- JsonNode helpers -------------------------------------------------------------------------

    private static JsonNode node(JsonNode parent, String field) {
        return parent == null ? null : parent.get(field);
    }

    private static String text(JsonNode parent, String field) {
        JsonNode value = node(parent, field);
        return value == null || value.isNull() ? null : value.asText();
    }

    private static Integer intOrNull(JsonNode value) {
        return value == null || value.isNull() ? null : value.asInt();
    }

    private static Double doubleOrNull(JsonNode value) {
        return value == null || value.isNull() ? null : value.asDouble();
    }
}
