package com.parallax.application.lab;

import com.parallax.application.json.CanonicalJson;
import com.parallax.engine.model.Decision;
import com.parallax.engine.model.RuleConfig;
import com.parallax.engine.scoring.DecisionEngine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * Runs one replay on the "replay" executor thread (SPEC §10): pages rows, evaluates each page in
 * parallel chunks (merged in chunk order for determinism), accumulates the report, writes progress,
 * then stores the report, flips and timings. On success a still-DRAFT version of the same config
 * becomes REPLAYED; on error the job is FAILED with the message.
 */
@Component
public class ReplayJobRunner {

    private static final Logger log = LoggerFactory.getLogger(ReplayJobRunner.class);

    private static final String INSERT_FLIP = """
            INSERT INTO replay_flip (job_id, ledger_seq, application_public_id, baseline_outcome,
                candidate_outcome, baseline_score, candidate_score, baseline_limit, candidate_limit,
                candidate_reasons, observed)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?)
            """;

    private final JdbcTemplate jdbc;
    private final ReplayLoader loader;
    private final CanonicalJson canonicalJson;
    private final Clock clock;

    public ReplayJobRunner(JdbcTemplate jdbc, ReplayLoader loader, CanonicalJson canonicalJson, Clock clock) {
        this.jdbc = jdbc;
        this.loader = loader;
        this.canonicalJson = canonicalJson;
        this.clock = clock;
    }

    /** Parameters captured at submit time so the async run is independent of later LIVE changes. */
    public record Job(String jobId, String candidateVersion, RuleConfig candidateConfig,
                      String candidateConfigHash, String baselineVersion, RuleConfig baselineConfig,
                      Instant from, Instant to) {
    }

    public void run(Job job) {
        long startNanos = System.nanoTime();
        jdbc.update("UPDATE replay_job SET status = 'RUNNING' WHERE id = ?", job.jobId());
        try {
            long total = loader.total(job.from(), job.to());
            jdbc.update("UPDATE replay_job SET total = ? WHERE id = ?", total, job.jobId());

            ReportAccumulator master = newAccumulator(job);
            long loadNanos = 0;
            long evalNanos = 0;
            long afterSeq = 0;
            long processed = 0;
            int processors = Runtime.getRuntime().availableProcessors();
            ExecutorService pool = Executors.newFixedThreadPool(Math.max(1, processors));
            try {
                while (true) {
                    long t0 = System.nanoTime();
                    List<ReplayRow> rows = loader.page(afterSeq, job.from(), job.to());
                    loadNanos += System.nanoTime() - t0;
                    if (rows.isEmpty()) {
                        break;
                    }
                    long t1 = System.nanoTime();
                    master.merge(evaluatePage(rows, job, pool, processors));
                    evalNanos += System.nanoTime() - t1;

                    afterSeq = rows.get(rows.size() - 1).seq();
                    processed += rows.size();
                    jdbc.update("UPDATE replay_job SET progress = ? WHERE id = ?", processed, job.jobId());
                    if (rows.size() < ReplayLoader.PAGE_SIZE) {
                        break;
                    }
                }
            } finally {
                pool.shutdown();
            }

            insertFlips(job.jobId(), master.flips());
            String reportJson = canonicalJson.write(master.toReport(job.baselineVersion(), job.candidateVersion()));
            long totalMs = (System.nanoTime() - startNanos) / 1_000_000;
            jdbc.update("UPDATE replay_job SET status = 'DONE', progress = ?, report = ?::jsonb, "
                            + "load_ms = ?, evaluate_ms = ?, total_ms = ?, finished_at = ? WHERE id = ?",
                    total, reportJson, loadNanos / 1_000_000, evalNanos / 1_000_000, totalMs,
                    Timestamp.from(Instant.now(clock)), job.jobId());

            // If the version is still DRAFT and matches this job's config, mark it REPLAYED (SPEC §10).
            jdbc.update("UPDATE rule_version SET status = 'REPLAYED' "
                            + "WHERE version = ? AND status = 'DRAFT' AND config_hash = ?",
                    job.candidateVersion(), job.candidateConfigHash());
            log.info("Replay {} DONE: {} rows, loadMs={}, evaluateMs={}, totalMs={}",
                    job.jobId(), total, loadNanos / 1_000_000, evalNanos / 1_000_000, totalMs);
        } catch (RuntimeException e) {
            log.warn("Replay {} FAILED: {}", job.jobId(), e.getMessage());
            jdbc.update("UPDATE replay_job SET status = 'FAILED', error = ?, finished_at = ? WHERE id = ?",
                    e.getMessage(), Timestamp.from(Instant.now(clock)), job.jobId());
        }
    }

    private ReportAccumulator evaluatePage(List<ReplayRow> rows, Job job, ExecutorService pool, int processors) {
        int size = rows.size();
        int chunks = Math.max(1, Math.min(processors, size));
        int chunkSize = (int) Math.ceil(size / (double) chunks);
        List<Future<ReportAccumulator>> futures = new ArrayList<>();
        for (int start = 0; start < size; start += chunkSize) {
            List<ReplayRow> slice = rows.subList(start, Math.min(start + chunkSize, size));
            futures.add(pool.submit(() -> {
                ReportAccumulator acc = newAccumulator(job);
                for (ReplayRow row : slice) {
                    Decision baseline = DecisionEngine.evaluate(row.input(), job.baselineConfig());
                    Decision candidate = DecisionEngine.evaluate(row.input(), job.candidateConfig());
                    acc.add(row, baseline, candidate);
                }
                return acc;
            }));
        }
        ReportAccumulator merged = newAccumulator(job);
        for (Future<ReportAccumulator> future : futures) {
            try {
                merged.merge(future.get());
            } catch (Exception e) {
                throw new IllegalStateException("Replay evaluation failed: " + e.getMessage(), e);
            }
        }
        return merged;
    }

    private ReportAccumulator newAccumulator(Job job) {
        return new ReportAccumulator(job.baselineConfig().ccf(), job.baselineConfig().lgd(),
                job.candidateConfig().ccf(), job.candidateConfig().lgd());
    }

    private void insertFlips(String jobId, List<FlipRow> flips) {
        if (flips.isEmpty()) {
            return;
        }
        jdbc.batchUpdate(INSERT_FLIP, new BatchPreparedStatementSetter() {
            @Override
            public void setValues(PreparedStatement ps, int i) throws SQLException {
                FlipRow f = flips.get(i);
                ps.setString(1, jobId);
                ps.setLong(2, f.seq());
                ps.setString(3, f.applicationPublicId());
                ps.setString(4, f.baselineOutcome());
                ps.setString(5, f.candidateOutcome());
                setInt(ps, 6, f.baselineScore());
                setInt(ps, 7, f.candidateScore());
                setInt(ps, 8, f.baselineLimit());
                setInt(ps, 9, f.candidateLimit());
                ps.setString(10, canonicalJson.write(f.candidateReasons()));
                ps.setBoolean(11, f.observed());
            }

            @Override
            public int getBatchSize() {
                return flips.size();
            }
        });
    }

    private static void setInt(PreparedStatement ps, int index, Integer value) throws SQLException {
        if (value == null) {
            ps.setNull(index, java.sql.Types.INTEGER);
        } else {
            ps.setInt(index, value);
        }
    }
}
