package com.parallax.bureau;

import com.parallax.bureau.SyntheticProfiles.Profile;
import com.parallax.bureau.SyntheticProfiles.Scenario;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class SyntheticProfilesTest {

    @Test
    void secondDigitMapsToProfile() {
        Profile[] expected = {
                Profile.PRIME, Profile.PRIME, Profile.PRIME,
                Profile.NEAR_PRIME, Profile.NEAR_PRIME, Profile.NEAR_PRIME,
                Profile.SUBPRIME, Profile.SUBPRIME,
                Profile.THIN_FILE,
                Profile.PRIME};
        for (int d = 0; d <= 9; d++) {
            String ssn = "9" + d + "0000000";
            assertThat(SyntheticProfiles.profileFor(ssn)).as("second digit %d", d).isEqualTo(expected[d]);
        }
    }

    @Test
    void thirdDigitMapsToScenario() {
        Scenario[] expected = {
                Scenario.NONE, Scenario.NONE, Scenario.NONE, Scenario.NONE,
                Scenario.NONE, Scenario.NONE, Scenario.NONE,
                Scenario.ADDRESS_MISMATCH,
                Scenario.SSN_BEFORE_DOB,
                Scenario.DECEASED};
        for (int d = 0; d <= 9; d++) {
            String ssn = "90" + d + "000000";
            assertThat(SyntheticProfiles.scenarioFor(ssn)).as("third digit %d", d).isEqualTo(expected[d]);
        }
    }

    @Test
    void profileValuesMatchSpec() {
        assertThat(Profile.PRIME.utilization()).isEqualByComparingTo(new BigDecimal("0.080"));
        assertThat(Profile.PRIME.inquiries6m()).isZero();
        assertThat(Profile.PRIME.delinquencies24m()).isZero();
        assertThat(Profile.PRIME.openTradelines()).isEqualTo(12);
        assertThat(Profile.PRIME.fileAgeMonths()).isEqualTo(156);

        assertThat(Profile.NEAR_PRIME.utilization()).isEqualByComparingTo(new BigDecimal("0.550"));
        assertThat(Profile.NEAR_PRIME.openTradelines()).isEqualTo(5);
        assertThat(Profile.NEAR_PRIME.fileAgeMonths()).isEqualTo(40);

        assertThat(Profile.SUBPRIME.utilization()).isEqualByComparingTo(new BigDecimal("0.820"));
        assertThat(Profile.SUBPRIME.inquiries6m()).isEqualTo(5);
        assertThat(Profile.SUBPRIME.delinquencies24m()).isEqualTo(2);
        assertThat(Profile.SUBPRIME.openTradelines()).isEqualTo(4);
        assertThat(Profile.SUBPRIME.fileAgeMonths()).isEqualTo(30);

        assertThat(Profile.THIN_FILE.utilization()).isEqualByComparingTo(new BigDecimal("0.200"));
        assertThat(Profile.THIN_FILE.inquiries6m()).isEqualTo(1);
        assertThat(Profile.THIN_FILE.openTradelines()).isEqualTo(1);
        assertThat(Profile.THIN_FILE.fileAgeMonths()).isEqualTo(10);
    }
}
