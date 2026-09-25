package com.parallax.application.error;

import java.util.List;

/**
 * A 400 validation error raised for input that Bean Validation cannot cover on its own — e.g. the
 * {@code Idempotency-Key} header (SPEC §15). Rendered as a ProblemDetail with a {@code fieldErrors}
 * array by {@link ApiExceptionHandler}.
 */
public class FieldValidationException extends RuntimeException {

    private final transient List<FieldErrorEntry> fieldErrors;

    public FieldValidationException(String detail, List<FieldErrorEntry> fieldErrors) {
        super(detail);
        this.fieldErrors = List.copyOf(fieldErrors);
    }

    public FieldValidationException(String detail, String field, String message) {
        this(detail, List.of(new FieldErrorEntry(field, message)));
    }

    public List<FieldErrorEntry> getFieldErrors() {
        return fieldErrors;
    }
}
