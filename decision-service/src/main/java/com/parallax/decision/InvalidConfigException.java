package com.parallax.decision;

import java.util.List;

/** Raised when {@code RuleConfigValidator} rejects the supplied config (SPEC §15 → 422). */
public class InvalidConfigException extends RuntimeException {

    private final transient List<String> errors;

    public InvalidConfigException(List<String> errors) {
        super("Invalid rule config: " + errors.size() + " error(s)");
        this.errors = List.copyOf(errors);
    }

    public List<String> getErrors() {
        return errors;
    }
}
