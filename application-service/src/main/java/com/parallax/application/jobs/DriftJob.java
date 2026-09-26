package com.parallax.application.jobs;

import com.parallax.application.drift.DriftService;
import com.parallax.application.drift.DriftViews;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Runs PSI drift monitoring nightly at 02:00 UTC and once at startup when no report exists yet and the
 * ledger has SEED history (SPEC §11). Advisory-lock guarded so only one instance runs it. A WATCH or
 * INVESTIGATE result logs a WARN alert (no PII).
 */
@Component
public class DriftJob {

    private static final long DRIFT_LOCK_KEY = 727277L;
    private static final Logger log = LoggerFactory.getLogger(DriftJob.class);

    private final JdbcTemplate jdbcTemplate;
    private final DriftService driftService;

    public DriftJob(JdbcTemplate jdbcTemplate, DriftService driftService) {
        this.jdbcTemplate = jdbcTemplate;
        this.driftService = driftService;
    }

    @Scheduled(cron = "${parallax.drift.cron:0 0 2 * * *}", zone = "UTC")
    void scheduled() {
        runOnce();
    }

    /** On startup, seed a first report only if none exists and there is SEED history to score. */
    @EventListener
    void onStartup(ApplicationReadyEvent event) {
        Integer reports = jdbcTemplate.queryForObject("SELECT count(*) FROM drift_report", Integer.class);
        Integer seedRows = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM decision_ledger WHERE source = 'SEED'", Integer.class);
        if ((reports == null || reports == 0) && seedRows != null && seedRows > 0) {
            runOnce();
        }
    }

    /** Run the drift report under the advisory lock; alert on WATCH/INVESTIGATE. */
    public void runOnce() {
        JobAdvisoryLock.runGuarded(jdbcTemplate, DRIFT_LOCK_KEY, () -> {
            DriftViews.Report report = driftService.run();
            if (!"stable".equals(report.status())) {
                log.warn("Drift alert psi={} status={}", report.psi(), report.status());
            }
            return List.of();
        });
    }
}
