package com.parallax.application.error;

import org.springframework.http.HttpStatus;

/**
 * A business error to be rendered as an RFC 7807 ProblemDetail (SPEC §15). Carries the HTTP status,
 * a short title and a human-readable detail. Used for conditions like 409 conflict, 422 unprocessable
 * and 404 not found. {@link ApiExceptionHandler} turns it into the response body.
 */
public class ApiProblem extends RuntimeException {

    private final HttpStatus status;
    private final String title;

    public ApiProblem(HttpStatus status, String title, String detail) {
        super(detail);
        this.status = status;
        this.title = title;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getTitle() {
        return title;
    }

    public String getDetail() {
        return getMessage();
    }
}
