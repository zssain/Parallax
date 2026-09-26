package com.parallax.application.error;

import java.util.List;

/**
 * An invalid rule configuration (SPEC §10): a 422 whose body carries an {@code errors} array of the
 * per-violation messages from {@code RuleConfigValidator}. Rendered by {@link ApiExceptionHandler}.
 */
public class ConfigValidationException extends RuntimeException {

    private final transient List<String> errors;

    public ConfigValidationException(String detail, List<String> errors) {
        super(detail);
        this.errors = List.copyOf(errors);
    }

    public List<String> getErrors() {
        return errors;
    }
}
