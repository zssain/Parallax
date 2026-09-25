package com.parallax.application.error;

/** One field-level validation error, matching the {field, message} contract in SPEC §15. */
public record FieldErrorEntry(String field, String message) {
}
