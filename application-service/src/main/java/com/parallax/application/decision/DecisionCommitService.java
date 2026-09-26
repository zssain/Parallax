package com.parallax.application.decision;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.parallax.application.domain.ApplicationEntity;
import com.parallax.application.domain.ApplicationRepository;
import com.parallax.application.domain.ApplicationStatus;
import com.parallax.application.idempotency.IdempotencyService;
import com.parallax.application.ledger.LedgerEntry;
import com.parallax.application.ledger.LedgerKind;
import com.parallax.application.ledger.LedgerRecord;
import com.parallax.application.ledger.LedgerSource;
import com.parallax.application.ledger.LedgerWriter;
import com.parallax.application.ledger.PartialEngineInput;
import com.parallax.application.pipeline.PipelineRecorder;
import com.parallax.application.pipeline.PipelineStatus;
import com.parallax.application.pipeline.PipelineStep;
import com.parallax.application.shadow.DecisionCommittedEvent;
import com.parallax.engine.api.EvaluateResponse;
import com.parallax.engine.model.Decision;
import com.parallax.engine.model.EngineInput;
import com.parallax.engine.model.ReasonCode;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import com.parallax.application.bureau.BureauReport;

/**
 * Commits a decision and its ledger row in ONE transaction (SPEC §3 7a/7b). The ledger append,
 * application status change, rule first_used_at stamp and idempotency completion all commit together;
 * any failure rolls the lot back. Returns the serialized 201 body (also stored under the key).
 */
@Service
public class DecisionCommitService {

    private final LedgerWriter ledgerWriter;
    private final ApplicationRepository applicationRepository;
    private final JdbcTemplate jdbcTemplate;
    private final IdempotencyService idempotency;
    private final PipelineRecorder pipeline;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final CommitFaultHook faultHook;
    private final ApplicationEventPublisher eventPublisher;

    public DecisionCommitService(LedgerWriter ledgerWriter, ApplicationRepository applicationRepository,
                                 JdbcTemplate jdbcTemplate, IdempotencyService idempotency,
                                 PipelineRecorder pipeline, ObjectMapper objectMapper, Clock clock,
                                 CommitFaultHook faultHook, ApplicationEventPublisher eventPublisher) {
        this.ledgerWriter = ledgerWriter;
        this.applicationRepository = applicationRepository;
        this.jdbcTemplate = jdbcTemplate;
        this.idempotency = idempotency;
        this.pipeline = pipeline;
        this.objectMapper = objectMapper;
        this.clock = clock;
        this.faultHook = faultHook;
        this.eventPublisher = eventPublisher;
    }

    /** SPEC §3 7a: DECISION/REDECISION row → DECIDED → first_used_at → key completed, one transaction. */
    @Transactional
    public String commitDecision(ApplicationEntity app, EngineInput input, BureauReport report,
                                 EvaluateResponse response, LedgerKind kind, Long linkedSeq,
                                 ClientKey clientKey, long commitStartNanos) {
        Decision decision = response.decision();
        LedgerRecord row = ledgerWriter.append(LedgerEntry.builder(kind, LedgerSource.LIVE)
                .application(app.getId(), app.getPublicId())
                .ruleVersion(response.ruleVersion())
                .scorecardVersion(response.scorecardVersion())
                .engineVersion(response.engineVersion())
                .bureau(report.pullId(), report.reused())
                .engineInput(input)
                .outcome(decision.outcome().name())
                .score(decision.score())
                .creditLimit(decision.creditLimit())
                .reasonCodes(names(decision.reasonCodes()))
                .fraudFlags(names(decision.fraudFlags()))
                .atpMax(decision.atpMax())
                .linkedSeq(linkedSeq)
                .build());
        faultHook.afterLedgerInsert();

        app.changeStatus(ApplicationStatus.DECIDED);
        app.setUpdatedAt(Instant.now(clock).truncatedTo(ChronoUnit.MICROS));
        applicationRepository.save(app);

        jdbcTemplate.update(
                "UPDATE rule_version SET first_used_at = ? WHERE version = ? AND first_used_at IS NULL",
                Timestamp.from(Instant.now(clock)), response.ruleVersion());

        // Shadow mode (SPEC §10): a LIVE DECISION/REDECISION with engine data is re-scored after commit.
        eventPublisher.publishEvent(new DecisionCommittedEvent(row.seq(), input, decision));

        // The scheduled re-decision/retry jobs run without a request: no pipeline, no idempotency key.
        if (!requestActive()) {
            return null;
        }

        pipeline.recordMs(PipelineStep.LEDGER_COMMIT, PipelineStatus.OK,
                elapsedMs(commitStartNanos), "seq #" + row.seq());

        List<DecideResponse.ReasonCodeView> reasons = decision.reasonCodes().stream()
                .filter(ReasonCode::applicantFacing)
                .map(rc -> new DecideResponse.ReasonCodeView(rc.name(), rc.description()))
                .toList();
        DecideResponse body = new DecideResponse(app.getPublicId(), ApplicationStatus.DECIDED.name(),
                decision.outcome().name(), decision.score(), decision.creditLimit(), reasons,
                response.ruleVersion(), row.seq(), row.createdAt(),
                new DecideResponse.BureauView(report.pullId(), report.reused()), pipeline.items());
        return complete(clientKey, app, body);
    }

    /** SPEC §3 7c: engine unavailable after retries → ENGINE_PENDING, engine_attempts=1, 202 body, one transaction. */
    @Transactional
    public String commitEnginePending(ApplicationEntity app, ClientKey clientKey, long commitStartNanos) {
        app.setEngineAttempts(1);
        app.changeStatus(ApplicationStatus.ENGINE_PENDING);
        app.setUpdatedAt(Instant.now(clock).truncatedTo(ChronoUnit.MICROS));
        applicationRepository.save(app);

        EnginePendingResponse body = new EnginePendingResponse(app.getPublicId(),
                ApplicationStatus.ENGINE_PENDING.name(), pipeline.items());
        String json = write(body);
        if (clientKey != null) {
            idempotency.completeInCurrentTransaction(clientKey.clientId(), clientKey.idemKey(), 202, json,
                    app.getPublicId());
        }
        return json;
    }

    private boolean requestActive() {
        return org.springframework.web.context.request.RequestContextHolder.getRequestAttributes() != null;
    }

    /** SPEC §3 7b: bureau-unavailable REFER/B01 row; status stays BUREAU_UNAVAILABLE. */
    @Transactional
    public String commitBureauUnavailable(ApplicationEntity app, PartialEngineInput partialInput,
                                          ClientKey clientKey, long commitStartNanos, String liveVersion) {
        LedgerRecord row = ledgerWriter.append(LedgerEntry.builder(LedgerKind.DECISION, LedgerSource.LIVE)
                .application(app.getId(), app.getPublicId())
                .ruleVersion(liveVersion)
                .engineInput(partialInput)
                .outcome("REFER")
                .score(null)
                .creditLimit(0)
                .reasonCodes(List.of("B01"))
                .fraudFlags(List.of())
                .atpMax(null)
                .build());

        pipeline.recordMs(PipelineStep.LEDGER_COMMIT, PipelineStatus.OK,
                elapsedMs(commitStartNanos), "seq #" + row.seq());

        DecideResponse body = new DecideResponse(app.getPublicId(),
                ApplicationStatus.BUREAU_UNAVAILABLE.name(), "REFER", null, 0, List.of(),
                liveVersion, row.seq(), row.createdAt(), null, pipeline.items());
        return complete(clientKey, app, body);
    }

    private String complete(ClientKey clientKey, ApplicationEntity app, DecideResponse body) {
        String json = write(body);
        if (clientKey != null) {
            idempotency.completeInCurrentTransaction(clientKey.clientId(), clientKey.idemKey(), 201, json,
                    app.getPublicId());
        }
        return json;
    }

    private static List<String> names(List<ReasonCode> codes) {
        return codes.stream().map(Enum::name).toList();
    }

    private long elapsedMs(long sinceNanos) {
        return Math.max(0, (System.nanoTime() - sinceNanos) / 1_000_000);
    }

    private String write(Object body) {
        try {
            return objectMapper.writeValueAsString(body);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialize decision response", e);
        }
    }
}
