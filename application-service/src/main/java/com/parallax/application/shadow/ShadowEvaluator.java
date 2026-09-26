package com.parallax.application.shadow;

import com.parallax.application.json.CanonicalJson;
import com.parallax.engine.model.Decision;
import com.parallax.engine.model.RuleConfig;
import com.parallax.engine.scoring.DecisionEngine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.event.TransactionPhase;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;

/**
 * Shadow mode (SPEC §10): after a live decision commits, if a version is enabled in shadow_config,
 * re-score the same engine input under that version's config in-process and store a shadow_result
 * (agrees = same outcome and same credit limit). Runs AFTER_COMMIT so it never affects the live
 * decision or its response, and any failure is logged (no PII) and swallowed.
 */
@Component
public class ShadowEvaluator {

    private static final Logger log = LoggerFactory.getLogger(ShadowEvaluator.class);

    private static final String INSERT = "INSERT INTO shadow_result"
            + " (ledger_seq, version, outcome, score, credit_limit, agrees, created_at)"
            + " VALUES (?, ?, ?, ?, ?, ?, ?) ON CONFLICT (ledger_seq) DO NOTHING";

    private final JdbcTemplate jdbc;
    private final CanonicalJson canonicalJson;
    private final ShadowFaultHook faultHook;
    private final Clock clock;

    public ShadowEvaluator(JdbcTemplate jdbc, CanonicalJson canonicalJson, ShadowFaultHook faultHook, Clock clock) {
        this.jdbc = jdbc;
        this.canonicalJson = canonicalJson;
        this.faultHook = faultHook;
        this.clock = clock;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onDecisionCommitted(DecisionCommittedEvent event) {
        try {
            String version = jdbc.query("SELECT version FROM shadow_config WHERE id = 1",
                    rs -> rs.next() ? rs.getString(1) : null);
            if (version == null) {
                return;
            }
            String configJson = jdbc.query("SELECT config::text FROM rule_version WHERE version = ?",
                    rs -> rs.next() ? rs.getString(1) : null, version);
            if (configJson == null) {
                return;
            }
            RuleConfig config = canonicalJson.read(configJson, RuleConfig.class);
            Decision shadow = DecisionEngine.evaluate(event.engineInput(), config);
            boolean agrees = shadow.outcome() == event.liveDecision().outcome()
                    && shadow.creditLimit() == event.liveDecision().creditLimit();

            faultHook.beforeShadowInsert();
            jdbc.update(INSERT, event.ledgerSeq(), version, shadow.outcome().name(), shadow.score(),
                    shadow.creditLimit(), agrees, Timestamp.from(Instant.now(clock)));
        } catch (RuntimeException e) {
            log.warn("Shadow evaluation failed for ledger seq {} — leaving no shadow result", event.ledgerSeq());
        }
    }
}
