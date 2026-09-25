package com.parallax.application.intake;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.PARAMETER;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * Optional phone: null/blank is valid; otherwise it must have 10–15 digits after stripping every
 * non-digit character (SPEC §15).
 */
@Documented
@Constraint(validatedBy = PhoneValidator.class)
@Target({FIELD, PARAMETER})
@Retention(RUNTIME)
public @interface ValidPhone {

    String message() default "phone must have 10 to 15 digits";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
