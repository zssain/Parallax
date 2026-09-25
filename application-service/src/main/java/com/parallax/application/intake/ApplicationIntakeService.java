package com.parallax.application.intake;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.parallax.application.bureau.BureauReport;
import com.parallax.application.bureau.BureauService;
import com.parallax.application.bureau.BureauUnavailableException;
import com.parallax.application.domain.ApplicationEntity;
import com.parallax.application.domain.ApplicationRepository;
import com.parallax.application.domain.ApplicationStatus;
import com.parallax.application.feature.DerivedFeatures;
import com.parallax.application.feature.EngineInputMapper;
import com.parallax.application.feature.FeatureService;
import com.parallax.application.idempotency.IdempotencyService;
import com.parallax.application.idempotency.Replay;
import com.parallax.application.json.CanonicalJson;
import com.parallax.application.pii.NameMasker;
import com.parallax.application.pii.Tokenizer;
import com.parallax.application.pipeline.PipelineRecorder;
import com.parallax.application.pipeline.PipelineStatus;
import com.parallax.application.pipeline.PipelineStep;
import com.parallax.engine.model.EngineInput;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

/**
 * Orchestrates intake through a ready {@link EngineInput} (SPEC §3 steps 1–5). Stops at BUREAU_PULLED;
 * Prompt 08 adds the ledger and Prompt 09 the decision. Idempotency begin/complete/abandon run in
 * their own transactions; this method's transaction covers the application and bureau_pull rows.
 */
@Service
public class ApplicationIntakeService {

    private final IdempotencyService idempotency;
    private final ApplicationRepository applicationRepository;
    private final BureauService bureauService;
    private final FeatureService featureService;
    private final EngineInputMapper engineInputMapper;
    private final PipelineRecorder pipeline;
    private final IntakeFailurePoint failurePoint;
    private final CanonicalJson canonicalJson;
    private final Tokenizer tokenizer;
    private final ObjectMapper objectMapper;

    public ApplicationIntakeService(IdempotencyService idempotency, ApplicationRepository applicationRepository,
                                    BureauService bureauService, FeatureService featureService,
                                    EngineInputMapper engineInputMapper, PipelineRecorder pipeline,
                                    IntakeFailurePoint failurePoint, CanonicalJson canonicalJson,
                                    Tokenizer tokenizer, ObjectMapper objectMapper) {
        this.idempotency = idempotency;
        this.applicationRepository = applicationRepository;
        this.bureauService = bureauService;
        this.featureService = featureService;
        this.engineInputMapper = engineInputMapper;
        this.pipeline = pipeline;
        this.failurePoint = failurePoint;
        this.canonicalJson = canonicalJson;
        this.tokenizer = tokenizer;
        this.objectMapper = objectMapper;
    }

    @Transactional
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

            ApplicationEntity application = save(buildReceived(clientId, request));

            long bureauStart = System.nanoTime();
            BureauReport report;
            try {
                report = bureauService.pullFor(application, request);
            } catch (BureauUnavailableException unavailable) {
                pipeline.record(PipelineStep.BUREAU, PipelineStatus.WARN, bureauStart, "bureau unavailable");
                pipeline.skip(PipelineStep.FRAUD_SCREEN, null);
                transition(application, ApplicationStatus.BUREAU_UNAVAILABLE);
                return complete(clientId, idemKey, application.getPublicId(),
                        ApplicationStatus.BUREAU_UNAVAILABLE.name(), null);
            }
            pipeline.record(PipelineStep.BUREAU, PipelineStatus.OK, bureauStart,
                    report.reused() ? "report reused (window " + bureauService.reuseDays() + " d)" : "fresh pull");
            transition(application, ApplicationStatus.BUREAU_PULLED);

            long fraudStart = System.nanoTime();
            DerivedFeatures features = featureService.derive(application, request, report);
            EngineInput engineInput = engineInputMapper.toEngineInput(application, report, features);
            pipeline.record(PipelineStep.FRAUD_SCREEN, PipelineStatus.OK, fraudStart, ""); // PX-9: detail

            return complete(clientId, idemKey, application.getPublicId(),
                    ApplicationStatus.BUREAU_PULLED.name(), engineInput);
        } catch (RuntimeException e) {
            idempotency.abandon(clientId, idemKey);
            throw e;
        }
    }

    private IntakeOutcome complete(String clientId, String idemKey, String applicationId,
                                   String status, EngineInput engineInputPreview) {
        IntakeResponse response = new IntakeResponse(applicationId, status, pipeline.items(), engineInputPreview);
        String body = writeJson(response);
        idempotency.complete(clientId, idemKey, 202, body, applicationId);
        return new IntakeOutcome(202, body, false);
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

    private ApplicationEntity save(ApplicationEntity application) {
        // IDENTITY generation inserts immediately, so the row is visible to the velocity query.
        return applicationRepository.save(application);
    }

    private void transition(ApplicationEntity application, ApplicationStatus to) {
        application.changeStatus(to);
        application.setUpdatedAt(Instant.now().truncatedTo(ChronoUnit.MICROS));
        applicationRepository.save(application);
    }

    /** A successful-creation replay (201/202) becomes 200; other statuses replay as stored (SPEC §7). */
    private int replayStatus(int storedStatus) {
        return (storedStatus == 201 || storedStatus == 202) ? 200 : storedStatus;
    }

    private String writeJson(IntakeResponse response) {
        try {
            return objectMapper.writeValueAsString(response);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialize intake response", e);
        }
    }
}
