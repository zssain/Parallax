package com.parallax.application.intake;

import com.parallax.application.bureau.BureauReport;
import com.parallax.application.bureau.BureauService;
import com.parallax.application.bureau.BureauUnavailableException;
import com.parallax.application.decision.ClientKey;
import com.parallax.application.decision.DecisionClient;
import com.parallax.application.decision.DecisionCommitService;
import com.parallax.application.decision.DecisionUnavailableException;
import com.parallax.application.domain.ApplicationEntity;
import com.parallax.application.domain.ApplicationRepository;
import com.parallax.application.domain.ApplicationStatus;
import com.parallax.application.feature.DerivedFeatures;
import com.parallax.application.feature.EngineInputMapper;
import com.parallax.application.feature.FeatureService;
import com.parallax.application.idempotency.IdempotencyService;
import com.parallax.application.idempotency.Replay;
import com.parallax.application.json.CanonicalJson;
import com.parallax.application.ledger.LedgerKind;
import com.parallax.application.ledger.PartialEngineInput;
import com.parallax.application.pii.NameMasker;
import com.parallax.application.pii.Tokenizer;
import com.parallax.application.pipeline.PipelineRecorder;
import com.parallax.application.pipeline.PipelineStatus;
import com.parallax.application.pipeline.PipelineStep;
import com.parallax.application.rules.LiveRule;
import com.parallax.application.rules.LiveRuleService;
import com.parallax.engine.api.EvaluateRequest;
import com.parallax.engine.api.EvaluateResponse;
import com.parallax.engine.model.Decision;
import com.parallax.engine.model.EngineInput;
import com.parallax.engine.model.PullType;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.Period;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

/**
 * Orchestrates the live decision flow (SPEC §3): idempotency, intake, bureau, features, decision and
 * a one-transaction commit that returns the final 201. Bureau failure degrades to a B01 REFER (7b);
 * an engine failure abandons the key and returns 503 for now (Prompt 10 adds the ENGINE_PENDING path).
 */
@Service
public class ApplicationIntakeService {

    private final IdempotencyService idempotency;
    private final ApplicationRepository applicationRepository;
    private final BureauService bureauService;
    private final FeatureService featureService;
    private final EngineInputMapper engineInputMapper;
    private final LiveRuleService liveRuleService;
    private final DecisionClient decisionClient;
    private final DecisionCommitService commitService;
    private final PipelineRecorder pipeline;
    private final IntakeFailurePoint failurePoint;
    private final CanonicalJson canonicalJson;
    private final Tokenizer tokenizer;

    public ApplicationIntakeService(IdempotencyService idempotency, ApplicationRepository applicationRepository,
                                    BureauService bureauService, FeatureService featureService,
                                    EngineInputMapper engineInputMapper, LiveRuleService liveRuleService,
                                    DecisionClient decisionClient, DecisionCommitService commitService,
                                    PipelineRecorder pipeline, IntakeFailurePoint failurePoint,
                                    CanonicalJson canonicalJson, Tokenizer tokenizer) {
        this.idempotency = idempotency;
        this.applicationRepository = applicationRepository;
        this.bureauService = bureauService;
        this.featureService = featureService;
        this.engineInputMapper = engineInputMapper;
        this.liveRuleService = liveRuleService;
        this.decisionClient = decisionClient;
        this.commitService = commitService;
        this.pipeline = pipeline;
        this.failurePoint = failurePoint;
        this.canonicalJson = canonicalJson;
        this.tokenizer = tokenizer;
    }

    public IntakeOutcome intake(String clientId, String idemKey, ApplicationRequest request) {
        String requestHash = canonicalJson.sha256Hex(canonicalJson.write(request));

        long idemStart = System.nanoTime();
        Optional<Replay> replay = idempotency.begin(clientId, idemKey, requestHash);
        pipeline.record(PipelineStep.IDEMPOTENCY, PipelineStatus.OK, idemStart, null);
        if (replay.isPresent()) {
            Replay stored = replay.get();
            return new IntakeOutcome(replayStatus(stored.status()), stored.body(), true);
        }

        try {
            failurePoint.afterKeyInsert();

            ApplicationEntity application = applicationRepository.save(buildReceived(clientId, request));
            ClientKey clientKey = new ClientKey(clientId, idemKey);
            LiveRule live = liveRuleService.current();

            long bureauStart = System.nanoTime();
            BureauReport report;
            try {
                report = bureauService.pullFor(application, request);
            } catch (CallNotPermittedException circuitOpen) {
                return bureauUnavailable(application, request, clientKey, live.version(), bureauStart, "circuit OPEN");
            } catch (BureauUnavailableException unavailable) {
                return bureauUnavailable(application, request, clientKey, live.version(), bureauStart, "bureau unavailable");
            }
            pipeline.record(PipelineStep.BUREAU, PipelineStatus.OK, bureauStart,
                    report.reused() ? "report reused (window " + bureauService.reuseDays() + " d)" : "fresh pull");
            transition(application, ApplicationStatus.BUREAU_PULLED);

            long fraudStart = System.nanoTime();
            DerivedFeatures features = featureService.derive(application, request, report);
            EngineInput input = engineInputMapper.toEngineInput(application, report, features);
            long fraudMs = elapsedMs(fraudStart);

            long engineStart = System.nanoTime();
            EvaluateResponse response;
            try {
                response = decisionClient.evaluate(new EvaluateRequest(live.version(), live.config(), input));
            } catch (DecisionUnavailableException unavailable) {
                // SPEC §3 7c: engine unavailable after retries → ENGINE_PENDING, 202, queued for the retry job.
                pipeline.recordMs(PipelineStep.FRAUD_SCREEN, PipelineStatus.OK, fraudMs, "");
                pipeline.recordMs(PipelineStep.ENGINE, "Decision engine · " + live.version(),
                        PipelineStatus.WARN, elapsedMs(engineStart), "engine unavailable, queued for retry");
                pipeline.skip(PipelineStep.LEDGER_COMMIT, null);
                String body = commitService.commitEnginePending(application, clientKey, System.nanoTime());
                return new IntakeOutcome(202, body, false);
            }
            long engineMs = elapsedMs(engineStart);

            Decision decision = response.decision();
            boolean hasFraud = !decision.fraudFlags().isEmpty();
            pipeline.recordMs(PipelineStep.FRAUD_SCREEN, hasFraud ? PipelineStatus.WARN : PipelineStatus.OK,
                    fraudMs, hasFraud ? decision.fraudFlags().size() + " flag(s)" : "");
            pipeline.recordMs(PipelineStep.ENGINE, "Decision engine · " + live.version(),
                    PipelineStatus.OK, engineMs, "");

            long commitStart = System.nanoTime();
            String body = commitService.commitDecision(application, input, report, response,
                    LedgerKind.DECISION, null, clientKey, commitStart);
            return new IntakeOutcome(201, body, false);
        } catch (RuntimeException e) {
            idempotency.abandon(clientId, idemKey);
            throw e;
        }
    }

    private ApplicationEntity buildReceived(String clientId, ApplicationRequest request) {
        String fullName = request.firstName() + " " + request.lastName();
        String ssn = request.ssn();

        ApplicationEntity application = new ApplicationEntity();
        application.setPublicId(applicationRepository.nextPublicId());
        application.setClientId(clientId);
        application.setName(fullName);
        application.setNameMasked(NameMasker.mask(fullName));
        application.setSsn(ssn);
        application.setSsnToken(tokenizer.ssnToken(ssn));
        application.setSsnLast4(ssn.substring(ssn.length() - 4));
        application.setDob(request.dateOfBirth().toString());
        application.setBirthYear(request.dateOfBirth().getYear());
        if (request.email() != null && !request.email().isBlank()) {
            application.setEmail(request.email());
            application.setEmailHash(tokenizer.emailHash(request.email()));
        }
        if (request.phone() != null && !request.phone().isBlank()) {
            application.setPhoneHash(tokenizer.phoneHash(request.phone()));
        }
        application.setAddress(request.address());
        application.setAnnualIncome(request.annualIncome());
        application.setMonthlyHousing(request.monthlyHousing());
        application.setMonthlyDebt(request.monthlyDebt());
        application.setIndependentIncome(request.independentIncome());
        application.setBureauConsent(request.bureauConsent());
        application.setProduct(request.product().name());
        application.setInitialStatus(ApplicationStatus.RECEIVED);
        application.setSource("LIVE");
        application.setEngineAttempts(0);
        Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);
        application.setCreatedAt(now);
        application.setUpdatedAt(now);
        return application;
    }

    private IntakeOutcome bureauUnavailable(ApplicationEntity application, ApplicationRequest request,
                                            ClientKey clientKey, String liveVersion, long bureauStart,
                                            String detail) {
        pipeline.record(PipelineStep.BUREAU, PipelineStatus.WARN, bureauStart, detail);
        pipeline.skip(PipelineStep.FRAUD_SCREEN, null);
        pipeline.skip(PipelineStep.ENGINE, null);
        transition(application, ApplicationStatus.BUREAU_UNAVAILABLE);
        String body = commitService.commitBureauUnavailable(application, partialInput(application, request),
                clientKey, System.nanoTime(), liveVersion);
        return new IntakeOutcome(201, body, false);
    }

    private PartialEngineInput partialInput(ApplicationEntity application, ApplicationRequest request) {
        LocalDate dob = request.dateOfBirth();
        LocalDate asOf = application.getCreatedAt().atZone(ZoneOffset.UTC).toLocalDate();
        int age = Period.between(dob, asOf).getYears();
        return PartialEngineInput.unavailable(age, dob.getYear(), application.getAnnualIncome(),
                application.getMonthlyHousing(), application.getMonthlyDebt(),
                application.isIndependentIncome(), application.isBureauConsent(), PullType.HARD);
    }

    private void transition(ApplicationEntity application, ApplicationStatus to) {
        application.changeStatus(to);
        application.setUpdatedAt(Instant.now().truncatedTo(ChronoUnit.MICROS));
        applicationRepository.save(application);
    }

    /** A created (201) decision replays as 200; other statuses (202 ENGINE_PENDING) replay unchanged (SPEC §7). */
    private int replayStatus(int storedStatus) {
        return storedStatus == 201 ? 200 : storedStatus;
    }

    private long elapsedMs(long sinceNanos) {
        return Math.max(0, (System.nanoTime() - sinceNanos) / 1_000_000);
    }
}
