package com.parallax.application.jobs;

import com.parallax.application.bureau.BureauReport;
import com.parallax.application.bureau.BureauService;
import com.parallax.application.decision.DecisionClient;
import com.parallax.application.decision.DecisionCommitService;
import com.parallax.application.decision.DecisionUnavailableException;
import com.parallax.application.domain.ApplicationEntity;
import com.parallax.application.domain.ApplicationRepository;
import com.parallax.application.domain.ApplicationStatus;
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

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

/**
 * Finishes applications stranded at ENGINE_PENDING (SPEC §6): rebuilds the input (bureau pull reused)
 * and re-evaluates. Success commits a DECISION row and DECIDED; failure bumps engine_attempts, and the
 * third failed attempt moves it to ENGINE_FAILED_MANUAL for the review queue.
 */
@Component
public class EngineRetryJob {

    /** Advisory lock key for the engine-retry job (SPEC §6). */
    private static final long ENGINE_RETRY_LOCK_KEY = 727276L;
    private static final int MAX_ATTEMPTS = 3;
    private static final Logger log = LoggerFactory.getLogger(EngineRetryJob.class);

    private final JdbcTemplate jdbcTemplate;
    private final ApplicationRepository applicationRepository;
    private final BureauService bureauService;
    private final FeatureService featureService;
    private final EngineInputMapper engineInputMapper;
    private final DecisionClient decisionClient;
    private final DecisionCommitService commitService;
    private final LiveRuleService liveRuleService;
    private final Clock clock;

    public EngineRetryJob(JdbcTemplate jdbcTemplate, ApplicationRepository applicationRepository,
                          BureauService bureauService, FeatureService featureService,
                          EngineInputMapper engineInputMapper, DecisionClient decisionClient,
                          DecisionCommitService commitService, LiveRuleService liveRuleService, Clock clock) {
        this.jdbcTemplate = jdbcTemplate;
        this.applicationRepository = applicationRepository;
        this.bureauService = bureauService;
        this.featureService = featureService;
        this.engineInputMapper = engineInputMapper;
        this.decisionClient = decisionClient;
        this.commitService = commitService;
        this.liveRuleService = liveRuleService;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${parallax.jobs.engine-retry-ms:60000}")
    void scheduled() {
        runOnce();
    }

    public List<String> runOnce() {
        return JobAdvisoryLock.runGuarded(jdbcTemplate, ENGINE_RETRY_LOCK_KEY, this::process);
    }

    private List<String> process() {
        List<Long> ids = jdbcTemplate.queryForList(
                "SELECT id FROM application WHERE status = 'ENGINE_PENDING' AND engine_attempts < ?"
                        + " ORDER BY created_at ASC LIMIT 50", Long.class, MAX_ATTEMPTS);
        List<String> decided = new ArrayList<>();
        for (Long id : ids) {
            ApplicationEntity app = applicationRepository.findById(id).orElseThrow();
            try {
                retry(app);
                decided.add(app.getPublicId());
            } catch (DecisionUnavailableException engineDown) {
                recordFailure(app);
            } catch (RuntimeException e) {
                log.warn("Engine retry failed for one application; leaving it for the next run");
            }
        }
        log.info("Engine-retry job decided {} application(s)", decided.size());
        return decided;
    }

    private void retry(ApplicationEntity app) {
        ApplicationRequest form = ApplicationForms.formOf(app);
        BureauReport report = bureauService.pullFor(app, form); // reuses the stored bureau pull
        DerivedFeatures features = featureService.derive(app, form, report);
        EngineInput input = engineInputMapper.toEngineInput(app, report, features);
        LiveRule live = liveRuleService.current();
        EvaluateResponse response = decisionClient.evaluate(new EvaluateRequest(live.version(), live.config(), input));
        commitService.commitDecision(app, input, report, response, LedgerKind.DECISION, null, null,
                System.nanoTime());
    }

    private void recordFailure(ApplicationEntity app) {
        int attempts = app.getEngineAttempts() + 1;
        app.setEngineAttempts(attempts);
        if (attempts >= MAX_ATTEMPTS) {
            app.changeStatus(ApplicationStatus.ENGINE_FAILED_MANUAL);
        }
        app.setUpdatedAt(Instant.now(clock).truncatedTo(ChronoUnit.MICROS));
        applicationRepository.save(app);
    }
}
