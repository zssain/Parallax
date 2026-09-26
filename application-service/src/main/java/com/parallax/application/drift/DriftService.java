package com.parallax.application.drift;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.parallax.application.config.AsOfDate;
import com.parallax.application.error.ApiProblem;
import com.parallax.application.json.CanonicalJson;
import com.parallax.application.rules.LiveRule;
import com.parallax.application.rules.LiveRuleService;
import com.parallax.engine.model.EngineInput;
import com.parallax.engine.model.RuleConfig;
import com.parallax.engine.scoring.DecisionEngine;
import com.parallax.generator.ApplicantGenerator;
import com.parallax.generator.GeneratedApplicant;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.SplittableRandom;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Population Stability Index drift monitoring (SPEC §11). The baseline is the development-era SEED
 * score distribution (rows older than as-of − 90 days); the current distribution is recent applicants
 * (as-of − 30 days .. as-of + 1 day). Both are re-scored under the LIVE config so drift reflects the
 * live scorecard, not each row's recorded score. All windows use the configured as-of date (invariant 10).
 */
@Service
public class DriftService {

    private static final long SIMULATE_SEED = 4242L;
    private static final int SIMULATE_COUNT = 5000;

    private final JdbcTemplate jdbc;
    private final CanonicalJson canonicalJson;
    private final ObjectMapper objectMapper;
    private final LiveRuleService liveRuleService;
    private final AsOfDate asOfDate;
    private final Clock clock;

    /** Baseline bin counts cached per (live version, as-of) — the baseline never changes for a run. */
    private final ConcurrentHashMap<String, long[]> baselineCache = new ConcurrentHashMap<>();

    public DriftService(JdbcTemplate jdbc, CanonicalJson canonicalJson, ObjectMapper objectMapper,
                        LiveRuleService liveRuleService, AsOfDate asOfDate, Clock clock) {
        this.jdbc = jdbc;
        this.canonicalJson = canonicalJson;
        this.objectMapper = objectMapper;
        this.liveRuleService = liveRuleService;
        this.asOfDate = asOfDate;
        this.clock = clock;
    }

    /** Compute the report over the current window, store it and return it. */
    @Transactional
    public DriftViews.Report run() {
        LocalDate asOf = asOfDate.get();
        LiveRule live = liveRuleService.current();
        long[] baseline = baselineCounts(live, asOf);
        long[] current = currentCounts(live, asOf);
        return store(live, asOf, baseline, current);
    }

    /** The newest stored report (404 when none exists). */
    @Transactional(readOnly = true)
    public DriftViews.Report latest() {
        DriftViews.Report report = jdbc.query("""
                SELECT id, created_at, as_of, live_version, baseline_n, current_n, bins::text AS bins, psi, status
                FROM drift_report ORDER BY id DESC LIMIT 1
                """, rs -> rs.next() ? readReport(rs) : null);
        if (report == null) {
            throw new ApiProblem(HttpStatus.NOT_FOUND, "Not Found", "No drift report yet");
        }
        return report;
    }

    /** Score 5,000 freshly generated applicants under LIVE with a market shift; never stored (SPEC §11). */
    @Transactional(readOnly = true)
    public DriftViews.Report simulate(double shift) {
        if (shift < 0.0 || shift > 1.5) {
            throw new ApiProblem(HttpStatus.UNPROCESSABLE_ENTITY, "Unprocessable Entity",
                    "shift must be between 0 and 1.5 (got " + shift + ")");
        }
        LocalDate asOf = asOfDate.get();
        LiveRule live = liveRuleService.current();
        long[] baseline = baselineCounts(live, asOf);

        long[] current = new long[PsiCalculator.BINS.length];
        SplittableRandom random = new SplittableRandom(SIMULATE_SEED);
        for (int i = 0; i < SIMULATE_COUNT; i++) {
            GeneratedApplicant applicant = ApplicantGenerator.next(random, shift, asOf.getYear());
            int score = DecisionEngine.evaluate(applicant.input(), live.config()).score();
            current[PsiCalculator.binIndex(score)]++;
        }
        return build(null, Instant.now(clock), asOf, live.version(), baseline, current, true);
    }

    /** Drop the cached baseline distributions. Used by tests that reseed the ledger under a fixed as-of. */
    public void clearBaselineCache() {
        baselineCache.clear();
    }

    // --- distributions ----------------------------------------------------------------------------

    private long[] baselineCounts(LiveRule live, LocalDate asOf) {
        String key = live.version() + "|" + asOf;
        return baselineCache.computeIfAbsent(key, k -> {
            Instant cutoff = asOf.minusDays(90).atStartOfDay(ZoneOffset.UTC).toInstant();
            return countBins(live.config(), """
                    SELECT engine_input::text FROM decision_ledger
                    WHERE source = 'SEED' AND kind = 'DECISION' AND created_at < ?
                      AND engine_input IS NOT NULL AND NOT jsonb_exists(engine_input, 'bureau')
                    """, Timestamp.from(cutoff));
        }).clone();
    }

    private long[] currentCounts(LiveRule live, LocalDate asOf) {
        Instant lower = asOf.minusDays(30).atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant upper = asOf.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();
        return countBins(live.config(), """
                SELECT engine_input::text FROM decision_ledger
                WHERE kind IN ('DECISION','REDECISION') AND created_at > ? AND created_at <= ?
                  AND engine_input IS NOT NULL AND NOT jsonb_exists(engine_input, 'bureau')
                """, Timestamp.from(lower), Timestamp.from(upper));
    }

    private long[] countBins(RuleConfig config, String sql, Object... params) {
        long[] bins = new long[PsiCalculator.BINS.length];
        jdbc.query(sql, rs -> {
            EngineInput input = canonicalJson.read(rs.getString(1), EngineInput.class);
            int score = DecisionEngine.evaluate(input, config).score();
            bins[PsiCalculator.binIndex(score)]++;
        }, params);
        return bins;
    }

    // --- report assembly --------------------------------------------------------------------------

    private DriftViews.Report store(LiveRule live, LocalDate asOf, long[] baseline, long[] current) {
        DriftViews.Report report = build(null, Instant.now(clock), asOf, live.version(), baseline, current, false);
        String binsJson = writeJson(report.bins());
        Long id = jdbc.queryForObject("""
                INSERT INTO drift_report (created_at, as_of, live_version, baseline_n, current_n, bins, psi, status)
                VALUES (?, ?, ?, ?, ?, ?::jsonb, ?, ?) RETURNING id
                """, Long.class, Timestamp.from(report.createdAt()), java.sql.Date.valueOf(asOf), live.version(),
                report.baselineN(), report.currentN(), binsJson,
                BigDecimal.valueOf(report.psi()), report.status());
        return new DriftViews.Report(id, report.createdAt(), asOf, live.version(), report.baselineN(),
                report.currentN(), report.bins(), report.psi(), report.status(), false);
    }

    private DriftViews.Report build(Long id, Instant createdAt, LocalDate asOf, String liveVersion,
                                    long[] baseline, long[] current, boolean simulated) {
        double[] baseProps = PsiCalculator.proportions(baseline);
        double[] currProps = PsiCalculator.proportions(current);
        double[] contributions = PsiCalculator.contributions(baseline, current);

        List<DriftViews.Bin> bins = new ArrayList<>(PsiCalculator.BINS.length);
        for (int i = 0; i < PsiCalculator.BINS.length; i++) {
            bins.add(new DriftViews.Bin(PsiCalculator.BINS[i][0], PsiCalculator.BINS[i][1],
                    baseProps[i], currProps[i], contributions[i]));
        }
        double psi = round5(PsiCalculator.psi(baseline, current));
        return new DriftViews.Report(id, createdAt, asOf, liveVersion, sum(baseline), sum(current),
                bins, psi, PsiCalculator.status(psi), simulated);
    }

    private DriftViews.Report readReport(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new DriftViews.Report(
                rs.getLong("id"),
                rs.getObject("created_at", OffsetDateTime.class).toInstant(),
                rs.getObject("as_of", LocalDate.class),
                rs.getString("live_version"),
                rs.getInt("baseline_n"),
                rs.getInt("current_n"),
                readBins(rs.getString("bins")),
                rs.getBigDecimal("psi").doubleValue(),
                rs.getString("status"),
                false);
    }

    private List<DriftViews.Bin> readBins(String json) {
        try {
            return List.of(objectMapper.readValue(json, DriftViews.Bin[].class));
        } catch (Exception e) {
            throw new IllegalStateException("Corrupt drift bins JSON", e);
        }
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialize drift bins", e);
        }
    }

    private static int sum(long[] counts) {
        long total = 0;
        for (long c : counts) {
            total += c;
        }
        return (int) total;
    }

    private static double round5(double value) {
        return BigDecimal.valueOf(value).setScale(5, RoundingMode.HALF_UP).doubleValue();
    }
}
