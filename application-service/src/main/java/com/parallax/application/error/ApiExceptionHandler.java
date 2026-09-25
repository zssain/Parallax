package com.parallax.application.error;

import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.ArrayList;
import java.util.List;

/**
 * Renders every uncaught error as an RFC 7807 ProblemDetail (SPEC §15). Validation failures add a
 * {@code fieldErrors} array; {@link ApiProblem} carries business status/title/detail; anything else
 * becomes a bare 500 with no stack trace or PII in the body.
 */
@RestControllerAdvice
public class ApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    /** Bean Validation on a request body → 400 with per-field messages. */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail onMethodArgumentNotValid(MethodArgumentNotValidException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Validation failed");
        problem.setTitle("Bad Request");
        List<FieldErrorEntry> fieldErrors = new ArrayList<>();
        for (FieldError fe : ex.getBindingResult().getFieldErrors()) {
            fieldErrors.add(new FieldErrorEntry(fe.getField(),
                    fe.getDefaultMessage() == null ? "invalid" : fe.getDefaultMessage()));
        }
        problem.setProperty("fieldErrors", fieldErrors);
        return problem;
    }

    /** Constraint violations on path/query params or @Validated methods → 400. */
    @ExceptionHandler(ConstraintViolationException.class)
    public ProblemDetail onConstraintViolation(ConstraintViolationException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Validation failed");
        problem.setTitle("Bad Request");
        List<FieldErrorEntry> fieldErrors = new ArrayList<>();
        ex.getConstraintViolations().forEach(v ->
                fieldErrors.add(new FieldErrorEntry(String.valueOf(v.getPropertyPath()), v.getMessage())));
        problem.setProperty("fieldErrors", fieldErrors);
        return problem;
    }

    /** Manual field validation (e.g. the Idempotency-Key header) → 400 with fieldErrors. */
    @ExceptionHandler(FieldValidationException.class)
    public ProblemDetail onFieldValidation(FieldValidationException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
        problem.setTitle("Bad Request");
        problem.setProperty("fieldErrors", ex.getFieldErrors());
        return problem;
    }

    /** Business errors carry their own status, title and detail (409, 422, 404, …). */
    @ExceptionHandler(ApiProblem.class)
    public ProblemDetail onApiProblem(ApiProblem ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(ex.getStatus(), ex.getDetail());
        problem.setTitle(ex.getTitle());
        return problem;
    }

    /** Anything unexpected → 500 without leaking a stack trace or PII to the client. */
    @ExceptionHandler(Exception.class)
    public ProblemDetail onUnexpected(Exception ex) {
        log.error("Unhandled exception", ex);
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred");
        problem.setTitle("Internal Server Error");
        return problem;
    }
}
