package com.parallax.engine.model;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ReasonCodeTest {

    @Test
    void descriptionsMatchSpecExactly() {
        assertThat(ReasonCode.R31.description()).isEqualTo("Proportion of revolving balances to credit limits is too high");
        assertThat(ReasonCode.R14.description()).isEqualTo("Too many recent inquiries for credit");
        assertThat(ReasonCode.R22.description()).isEqualTo("Delinquency on one or more accounts in the past 24 months");
        assertThat(ReasonCode.R07.description()).isEqualTo("Insufficient number of established credit accounts");
        assertThat(ReasonCode.R05.description()).isEqualTo("Length of credit history is too short");
        assertThat(ReasonCode.R33.description()).isEqualTo("Income insufficient for the amount of credit requested");
        assertThat(ReasonCode.P01.description()).isEqualTo("Income insufficient to support required minimum payments");
        assertThat(ReasonCode.P02.description()).isEqualTo("Applicant does not meet the minimum age requirement");
        assertThat(ReasonCode.P03.description()).isEqualTo("Applicant under 21 without independent income or co-signer");
        assertThat(ReasonCode.P04.description()).isEqualTo("Consent for a credit bureau inquiry was not provided");
        assertThat(ReasonCode.B01.description()).isEqualTo("Credit report temporarily unavailable (internal)");
        assertThat(ReasonCode.F01.description()).isEqualTo("Address does not match the credit file (internal)");
        assertThat(ReasonCode.F02.description()).isEqualTo("SSN issuance precedes date of birth (internal)");
        assertThat(ReasonCode.F03.description()).isEqualTo("SSN reported deceased (internal)");
        assertThat(ReasonCode.F04.description()).isEqualTo("Application velocity limit exceeded (internal)");
    }

    @Test
    void applicantFacingIsFalseExactlyForB01AndFraudFlags() {
        for (ReasonCode code : ReasonCode.values()) {
            boolean internal = code == ReasonCode.B01 || code == ReasonCode.F01
                    || code == ReasonCode.F02 || code == ReasonCode.F03 || code == ReasonCode.F04;
            assertThat(code.applicantFacing())
                    .as("applicantFacing for %s", code)
                    .isEqualTo(!internal);
        }
    }

    @Test
    void isFraudIsTrueExactlyForF01ToF04() {
        for (ReasonCode code : ReasonCode.values()) {
            boolean fraud = code == ReasonCode.F01 || code == ReasonCode.F02
                    || code == ReasonCode.F03 || code == ReasonCode.F04;
            assertThat(code.isFraud()).as("isFraud for %s", code).isEqualTo(fraud);
        }
    }
}
