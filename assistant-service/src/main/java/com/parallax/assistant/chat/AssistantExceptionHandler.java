package com.parallax.assistant.chat;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Renders assistant errors as RFC 7807 ProblemDetail (SPEC §15). */
@RestControllerAdvice
public class AssistantExceptionHandler {

    /** No model configured → 503 with the exact title "Assistant model not configured" (SPEC §12). */
    @ExceptionHandler(AssistantNotConfiguredException.class)
    public ProblemDetail onNotConfigured(AssistantNotConfiguredException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE, ex.getMessage());
        problem.setTitle("Assistant model not configured");
        return problem;
    }
}
