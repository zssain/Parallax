package com.parallax.application.overview;

import com.parallax.application.bureau.BureauCircuitControl;
import com.parallax.application.config.AsOfDate;
import com.parallax.application.query.ApplicationViews;
import com.parallax.application.query.DecisionQueryService;
import com.parallax.application.rules.LiveRule;
import com.parallax.application.rules.LiveRuleService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The Overview landing screen (SPEC §15): headline KPIs, the "needs attention" list, the outcome mix,
 * a 12-month approval trend and recent decisions. Decision counts use LIVE-source applications; all
 * time windows use the configured as-of date (invariant 10).
 */
@Service
public class OverviewService {

    /** Current row per LIVE-source application, grouped by outcome. */
    private static final String COUNTS_SQL = """
            SELECT c.outcome AS outcome, count(*) AS n FROM (
                SELECT DISTINCT ON (dl.application_id) dl.outcome
                FROM decision_ledger dl JOIN application a ON a.id = dl.application_id
                WHERE a.source = 'LIVE'
                ORDER BY dl.application_id, dl.seq DESC
            ) c GROUP BY c.outcome
            """;

    private static final String REVIEW_QUEUE_SQL = """
            SELECT count(*) FROM application a
            LEFT JOIN LATERAL (SELECT outcome FROM decision_ledger d
                               WHERE d.application_id = a.id ORDER BY d.seq DESC LIMIT 1) dl ON true
            WHERE (a.status IN ('DECIDED','BUREAU_UNAVAILABLE') AND dl.outcome = 'REFER')
               OR a.status = 'ENGINE_FAILED_MANUAL'
            """;

    private static final String REDECISION_QUEUE_SQL = """
            SELECT count(*) FROM application a
            JOIN LATERAL (SELECT kind, outcome, reason_codes FROM decision_ledger d
                          WHERE d.application_id = a.id ORDER BY d.seq DESC LIMIT 1) dl ON true
            WHERE a.status = 'BUREAU_UNAVAILABLE'
              AND dl.kind = 'DECISION' AND dl.outcome = 'REFER' AND dl.reason_codes @> '["B01"]'::jsonb
            """;

    private static final String TREND_SQL = """
            SELECT to_char((created_at AT TIME ZONE 'UTC'), 'YYYY-MM') AS ym,
                   count(*) AS total,
                   count(*) FILTER (WHERE outcome = 'APPROVED') AS approved
            FROM decision_ledger
            WHERE kind IN ('DECISION','REDECISION')
              AND NOT (coalesce(reason_codes, '[]'::jsonb) @> '["B01"]'::jsonb)
            GROUP BY ym
            """;

    private final JdbcTemplate jdbc;
    private final LiveRuleService liveRuleService;
    private final DecisionQueryService decisionQueryService;
    private final BureauCircuitControl circuit;
    private final AsOfDate asOfDate;

    public OverviewService(JdbcTemplate jdbc, LiveRuleService liveRuleService,
                           DecisionQueryService decisionQueryService, BureauCircuitControl circuit,
                           AsOfDate asOfDate) {
        this.jdbc = jdbc;
        this.liveRuleService = liveRuleService;
        this.decisionQueryService = decisionQueryService;
        this.circuit = circuit;
        this.asOfDate = asOfDate;
    }

    @Transactional(readOnly = true)
    public OverviewViews.Overview overview(String role) {
        Map<String, Long> counts = new HashMap<>();
        jdbc.query(COUNTS_SQL, rs -> {
            counts.put(rs.getString("outcome"), rs.getLong("n"));
        });
        long approved = counts.getOrDefault("APPROVED", 0L);
        long refer = counts.getOrDefault("REFER", 0L);
        long declined = counts.getOrDefault("DECLINED", 0L);
        long decisions = approved + refer + declined;
        double approvalRate = decisions == 0 ? 0.0 : round4((double) approved / decisions);

        long reviewQueue = count(REVIEW_QUEUE_SQL);
        long redecisionQueue = count(REDECISION_QUEUE_SQL);

        LiveRule live = liveRuleService.current();
        OverviewViews.LiveVersion liveVersion = new OverviewViews.LiveVersion(
                live.version(), live.since().atOffset(ZoneOffset.UTC).toLocalDate());

        OverviewViews.Psi psi = jdbc.query(
                "SELECT psi, status FROM drift_report ORDER BY id DESC LIMIT 1",
                rs -> rs.next() ? new OverviewViews.Psi(rs.getBigDecimal("psi").doubleValue(),
                        rs.getString("status")) : null);

        List<OverviewViews.Proposed> proposedVersions = jdbc.query(
                "SELECT version, proposed_by FROM rule_version WHERE status = 'PROPOSED' ORDER BY version",
                (rs, i) -> new OverviewViews.Proposed(rs.getString("version"), rs.getString("proposed_by")));

        String shadowVersion = jdbc.query("SELECT version FROM shadow_config WHERE id = 1",
                rs -> rs.next() ? rs.getString(1) : null);

        LocalDate asOf = asOfDate.get();
        List<OverviewViews.TrendPoint> approvalTrend = approvalTrend(asOf);
        List<ApplicationViews.ListItem> recent =
                decisionQueryService.list(role, null, null, "LIVE", 0, 7).items();

        List<OverviewViews.Attention> attention = attention(reviewQueue, proposedVersions,
                circuit.state(), redecisionQueue, psi, shadowVersion);

        return new OverviewViews.Overview(decisions, approved, refer, declined, approvalRate, reviewQueue,
                liveVersion, psi, circuit.state(), redecisionQueue, proposedVersions, shadowVersion,
                approvalTrend, recent, attention);
    }

    // --- approval trend ---------------------------------------------------------------------------

    private List<OverviewViews.TrendPoint> approvalTrend(LocalDate asOf) {
        Map<String, long[]> byMonth = new HashMap<>();
        jdbc.query(TREND_SQL, rs -> {
            byMonth.put(rs.getString("ym"), new long[]{rs.getLong("total"), rs.getLong("approved")});
        });
        YearMonth end = YearMonth.from(asOf);
        List<OverviewViews.TrendPoint> trend = new ArrayList<>(12);
        for (int i = 11; i >= 0; i--) {
            YearMonth month = end.minusMonths(i);
            String key = month.toString(); // YYYY-MM
            long[] row = byMonth.get(key);
            Double rate = (row == null || row[0] == 0) ? null : round4((double) row[1] / row[0]);
            trend.add(new OverviewViews.TrendPoint(key, rate));
        }
        return trend;
    }

    // --- attention --------------------------------------------------------------------------------

    private List<OverviewViews.Attention> attention(long reviewQueue,
                                                    List<OverviewViews.Proposed> proposedVersions,
                                                    String bureauCircuit, long redecisionQueue,
                                                    OverviewViews.Psi psi, String shadowVersion) {
        List<OverviewViews.Attention> items = new ArrayList<>();

        if (reviewQueue > 0) {
            items.add(new OverviewViews.Attention(
                    reviewQueue + " application" + (reviewQueue == 1 ? "" : "s") + " waiting in the review queue",
                    "queue", "warn"));
        }
        for (OverviewViews.Proposed proposed : proposedVersions) {
            items.add(new OverviewViews.Attention(
                    proposed.version() + " proposed by " + proposed.proposedBy() + " — needs a second approver",
                    "lab:" + proposed.version(), "info"));
        }
        if ("OPEN".equals(bureauCircuit)) {
            items.add(new OverviewViews.Attention(
                    "Credit bureau circuit is OPEN — new applications fall back to REFER", "system", "bad"));
        }
        if (redecisionQueue > 0) {
            items.add(new OverviewViews.Attention(
                    redecisionQueue + " bureau-outage REFER" + (redecisionQueue == 1 ? "" : "s")
                            + " waiting for automatic re-decision", "system", "warn"));
        }
        if (psi != null) {
            String severity = switch (psi.status()) {
                case "watch" -> "warn";
                case "investigate" -> "bad";
                default -> "ok";
            };
            items.add(new OverviewViews.Attention(
                    "Score drift PSI " + String.format(Locale.US, "%.3f", psi.value()) + " — " + psi.status(),
                    "drift", severity));
        }
        if (shadowVersion != null) {
            items.add(new OverviewViews.Attention(
                    shadowVersion + " is running in shadow mode on live traffic", "lab:" + shadowVersion, "acc"));
        }
        return items;
    }

    // --- helpers ----------------------------------------------------------------------------------

    private long count(String sql) {
        Long n = jdbc.queryForObject(sql, Long.class);
        return n == null ? 0 : n;
    }

    private static double round4(double value) {
        return BigDecimal.valueOf(value).setScale(4, RoundingMode.HALF_UP).doubleValue();
    }
}
