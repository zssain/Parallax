package com.parallax.application.intake;

import com.parallax.application.error.FieldValidationException;
import com.parallax.application.pipeline.PipelineRecorder;
import com.parallax.application.pipeline.PipelineStatus;
import com.parallax.application.pipeline.PipelineStep;
import com.parallax.application.security.CurrentUser;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

/** {@code POST /api/v1/applications} — intake (SPEC §3, §15). Returns 202 in this prompt. */
@RestController
public class ApplicationController {

    private static final String IDEMPOTENCY_HEADER = "Idempotency-Key";

    private final ApplicationIntakeService intakeService;
    private final CurrentUser currentUser;
    private final PipelineRecorder pipeline;

    public ApplicationController(ApplicationIntakeService intakeService, CurrentUser currentUser,
                                 PipelineRecorder pipeline) {
        this.intakeService = intakeService;
        this.currentUser = currentUser;
        this.pipeline = pipeline;
    }

    @PostMapping("/api/v1/applications")
    public ResponseEntity<String> create(
            @RequestHeader(value = IDEMPOTENCY_HEADER, required = false) String idempotencyKey,
            @Valid @RequestBody ApplicationRequest request) {

        // VALIDATE: from the filter's request start to controller entry (bean validation already ran).
        pipeline.record(PipelineStep.VALIDATE, PipelineStatus.OK, pipeline.startNanos(), null);
        requireValidIdempotencyKey(idempotencyKey);

        IntakeOutcome outcome = intakeService.intake(currentUser.username(), idempotencyKey, request);

        ResponseEntity.BodyBuilder builder = ResponseEntity.status(outcome.status())
                .contentType(MediaType.APPLICATION_JSON);
        if (outcome.replay()) {
            builder.header("Idempotent-Replay", "true");
        }
        return builder.body(outcome.jsonBody());
    }

    private void requireValidIdempotencyKey(String key) {
        if (key == null || key.isBlank() || key.length() > 128) {
            throw new FieldValidationException(
                    "The Idempotency-Key header is required and must be 1 to 128 characters",
                    IDEMPOTENCY_HEADER, "must be present and 1 to 128 characters");
        }
    }
}
