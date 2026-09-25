package com.parallax.decision;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * RFC 7807 error responses for the evaluate endpoint (SPEC §15): 422 for an invalid config (with an
 * {@code errors} array), 400 for a malformed body or an out-of-range EngineInput, 500 otherwise.
 */
@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(InvalidConfigException.class)
    public ProblemDetail onInvalidConfig(InvalidConfigException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.UNPROCESSABLE_ENTITY, "The supplied rule config is invalid");
        problem.setTitle("Unprocessable Entity");
        problem.setProperty("errors", ex.getErrors());
        return problem;
    }

    /** A malformed body or an EngineInput that fails its range checks (IllegalArgumentException). */
    @ExceptionHandler({HttpMessageNotReadableException.class, IllegalArgumentException.class})
    public ProblemDetail onBadRequest(Exception ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST, rootMessage(ex));
        problem.setTitle("Bad Request");
        return problem;
    }

    @ExceptionHandler(Exception.class)
    public ProblemDetail onUnexpected(Exception ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred");
        problem.setTitle("Internal Server Error");
        return problem;
    }

    private static String rootMessage(Throwable ex) {
        Throwable cause = ex;
        while (cause.getCause() != null && cause.getCause() != cause) {
            cause = cause.getCause();
        }
        return cause.getMessage() == null ? "Invalid request" : cause.getMessage();
    }
}
