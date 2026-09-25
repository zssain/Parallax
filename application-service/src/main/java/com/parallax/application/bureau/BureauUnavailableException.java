package com.parallax.application.bureau;

/** The credit bureau could not be reached (timeout, SOAP fault, 5xx). Circuit breaking arrives in Prompt 10. */
public class BureauUnavailableException extends RuntimeException {

    public BureauUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
