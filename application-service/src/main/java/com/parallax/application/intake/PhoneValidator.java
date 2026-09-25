package com.parallax.application.intake;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

/** Validates {@link ValidPhone}: optional; when present, 10–15 digits after stripping non-digits. */
public class PhoneValidator implements ConstraintValidator<ValidPhone, String> {

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value == null || value.isBlank()) {
            return true;
        }
        int digits = value.replaceAll("\\D", "").length();
        return digits >= 10 && digits <= 15;
    }
}
