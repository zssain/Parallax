package com.parallax.application.intake;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/**
 * The {@code POST /api/v1/applications} request body with Bean Validation exactly as SPEC §15.
 * No identity value here is ever logged; the record is also hashed (canonical JSON) for idempotency.
 */
public record ApplicationRequest(
        @NotBlank @Size(min = 1, max = 60) String firstName,
        @NotBlank @Size(min = 1, max = 60) String lastName,
        @NotNull @Past LocalDate dateOfBirth,
        @NotNull @Pattern(regexp = "^9\\d{8}$", message = "ssn must be 9 digits starting with 9") String ssn,
        @Email String email,
        @ValidPhone String phone,
        @NotBlank @Size(min = 6, max = 200) String address,
        @NotNull @Min(1) @Max(10_000_000) Integer annualIncome,
        @NotNull @Min(0) @Max(100_000) Integer monthlyHousing,
        @NotNull @Min(0) @Max(100_000) Integer monthlyDebt,
        @NotNull Boolean independentIncome,
        @NotNull Boolean bureauConsent,
        @NotNull Product product) {
}
