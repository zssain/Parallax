package com.parallax.account.error;

import org.springframework.http.HttpStatus;

/**
 * A business error rendered as an RFC 7807 ProblemDetail (SPEC §15): status, title and detail. Used for
 * 404 not found, 409 conflict and 422 unprocessable. {@link ApiExceptionHandler} turns it into the body.
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
