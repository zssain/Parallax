package com.parallax.account.error;

/** One entry of a ProblemDetail {@code fieldErrors} array (SPEC §15). */
public record FieldErrorEntry(String field, String message) {
}
