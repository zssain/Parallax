package com.parallax.account.collections;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Recomputes account status from days_past_due (SPEC §13): DELINQUENT at ≥ 30 days, else CURRENT. Runs
 * on the configured cron (default daily at 03:00 UTC) and can be triggered directly via {@link #runOnce()}.
 * Never touches CHARGED_OFF accounts.
 */
@Component
public class DelinquencyJob {

    private static final Logger log = LoggerFactory.getLogger(DelinquencyJob.class);

    private final JdbcTemplate jdbc;

    public DelinquencyJob(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Scheduled(cron = "${parallax.delinquency.cron:0 0 3 * * *}", zone = "UTC")
    void scheduled() {
        runOnce();
    }

    /** Recompute CURRENT/DELINQUENT from days_past_due; returns the number of rows considered. */
    @Transactional
    public int runOnce() {
        int updated = jdbc.update(
                "UPDATE account SET status = CASE WHEN days_past_due >= 30 THEN 'DELINQUENT' ELSE 'CURRENT' END "
                        + "WHERE status <> 'CHARGED_OFF'");
        log.info("Delinquency job recomputed status for {} account(s)", updated);
        return updated;
    }
}
