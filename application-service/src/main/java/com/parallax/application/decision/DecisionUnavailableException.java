package com.parallax.application.decision;

/** The decision engine could not be reached (I/O error or 5xx). Retry/ENGINE_PENDING arrive in Prompt 10. */
public class DecisionUnavailableException extends RuntimeException {

    public DecisionUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
