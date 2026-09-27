package com.parallax.application.jobs;

import com.parallax.application.bureau.BureauCircuitControl;
import com.parallax.application.bureau.BureauReport;
import com.parallax.application.bureau.BureauService;
import com.parallax.application.decision.DecisionCommitService;
import com.parallax.application.domain.ApplicationEntity;
import com.parallax.application.domain.ApplicationRepository;
import com.parallax.application.decision.DecisionClient;
import com.parallax.application.feature.DerivedFeatures;
import com.parallax.application.feature.EngineInputMapper;
import com.parallax.application.feature.FeatureService;
import com.parallax.application.intake.ApplicationRequest;
import com.parallax.application.ledger.LedgerKind;
import com.parallax.application.rules.LiveRule;
import com.parallax.application.rules.LiveRuleService;
import com.parallax.engine.api.EvaluateRequest;
import com.parallax.engine.api.EvaluateResponse;
import com.parallax.engine.model.EngineInput;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Finishes applications stranded at BUREAU_UNAVAILABLE once the bureau recovers (SPEC §6): re-pulls,
 * re-derives and re-decides each, appending a REDECISION row linked to its B01 row in one transaction.
 * Skips any application an underwriter already reviewed (its latest row is no longer the B01 DECISION).
 */
@Component
public class RedecisionJob {

    /** Advisory lock key for the re-decision job (SPEC §6). */
    private static final long REDECISION_LOCK_KEY = 727275L;
    private static final Logger log = LoggerFactory.getLogger(RedecisionJob.class);

    private static final String SELECT_PENDING = """
            SELECT a.id AS app_id, a.public_id, dl.seq AS b01_seq
            FROM application a
            JOIN LATERAL (SELECT seq, kind, outcome, reason_codes FROM decision_ledger d
                          WHERE d.application_id = a.id ORDER BY d.seq DESC LIMIT 1) dl ON true
            WHERE a.status = 'BUREAU_UNAVAILABLE'
              AND dl.kind = 'DECISION' AND dl.outcome = 'REFER' AND dl.reason_codes @> '["B01"]'::jsonb
            ORDER BY a.created_at ASC
            LIMIT 50
            """;

    private final JdbcTemplate jdbcTemplate;
    private final ApplicationRepository applicationRepository;
    private final BureauService bureauService;
    private final FeatureService featureService;
    private final EngineInputMapper engineInputMapper;
    private final DecisionClient decisionClient;
    private final DecisionCommitService commitService;
    private final LiveRuleService liveRuleService;
    private final BureauCircuitControl circuit;

    public RedecisionJob(JdbcTemplate jdbcTemplate, ApplicationRepository applicationRepository,
                         BureauService bureauService, FeatureService featureService,
                         EngineInputMapper engineInputMapper, DecisionClient decisionClient,
                         DecisionCommitService commitService, LiveRuleService liveRuleService,
                         BureauCircuitControl circuit) {
        this.jdbcTemplate = jdbcTemplate;
        this.applicationRepository = applicationRepository;
        this.bureauService = bureauService;
        this.featureService = featureService;
        this.engineInputMapper = engineInputMapper;
        this.decisionClient = decisionClient;
        this.commitService = commitService;
        this.liveRuleService = liveRuleService;
        this.circuit = circuit;
    }

    @Scheduled(fixedDelayString = "${parallax.jobs.redecision-ms:60000}",
            initialDelayString = "${parallax.jobs.initial-delay-ms:0}")
    void scheduled() {
        runOnce();
    }

    /** Process one batch; returns the public ids re-decided. */
    public List<String> runOnce() {
        if (circuit.isOpen()) {
            return List.of();
        }
        return JobAdvisoryLock.runGuarded(jdbcTemplate, REDECISION_LOCK_KEY, this::process);
    }

    private List<String> process() {
        List<Pending> pending = jdbcTemplate.query(SELECT_PENDING, (rs, i) ->
                new Pending(rs.getLong("app_id"), rs.getString("public_id"), rs.getLong("b01_seq")));
        List<String> processed = new ArrayList<>();
        for (Pending p : pending) {
            try {
                redecide(p);
                processed.add(p.publicId());
            } catch (RuntimeException e) {
                log.warn("Re-decision failed for one application; leaving it for the next run");
            }
        }
        log.info("Re-decision job processed {} application(s)", processed.size());
        return processed;
    }

    private void redecide(Pending pending) {
        ApplicationEntity app = applicationRepository.findById(pending.appId()).orElseThrow();
        ApplicationRequest form = ApplicationForms.formOf(app);
        BureauReport report = bureauService.pullFor(app, form);
        DerivedFeatures features = featureService.derive(app, form, report); // velocity as of the original time
        EngineInput input = engineInputMapper.toEngineInput(app, report, features);
        LiveRule live = liveRuleService.current();
        EvaluateResponse response = decisionClient.evaluate(new EvaluateRequest(live.version(), live.config(), input));
        commitService.commitDecision(app, input, report, response, LedgerKind.REDECISION, pending.b01Seq(),
                null, System.nanoTime());
    }

    private record Pending(long appId, String publicId, long b01Seq) {
    }
}
