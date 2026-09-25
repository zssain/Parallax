package com.parallax.application.intake;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Map;

import static com.github.tomakehurst.wiremock.client.WireMock.containing;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class IntakeBureauIT extends AbstractIntakeIT {

    private static final String USER = "priya.menon@parallax.dev";
    private static final String PRIME_SSN = "912345678";

    @Test
    void happyPathReachesBureauPulledWithFourPipelineSteps() throws Exception {
        stubBureau(PRIME_SSN, primeResponse("BP-PRIME01", "48 Elm Street, Columbus OH"));

        MvcResult result = submit(USER, newKey(), defaultRequest())
                .andExpect(status().isAccepted())
                .andReturn();

        JsonNode body = read(result);
        assertThat(body.get("status").asText()).isEqualTo("BUREAU_PULLED");
        assertThat(stepNames(body)).containsExactly("VALIDATE", "IDEMPOTENCY", "BUREAU", "FRAUD_SCREEN");
        assertThat(count("application")).isEqualTo(1);
        assertThat(count("bureau_pull")).isEqualTo(1);
    }

    @Test
    void secondApplicationReusesTheReportWithinTheWindow() throws Exception {
        stubBureau(PRIME_SSN, primeResponse("BP-PRIME01", "48 Elm Street, Columbus OH"));

        submit(USER, newKey(), defaultRequest()).andExpect(status().isAccepted());
        MvcResult second = submit(USER, newKey(), defaultRequest())
                .andExpect(status().isAccepted())
                .andReturn();

        // Only one SOAP call for this SSN; the second application reused the stored pull.
        BUREAU.verify(1, postRequestedFor(urlEqualTo("/ws")).withRequestBody(containing(PRIME_SSN)));
        assertThat(step(read(second), "BUREAU").get("detail").asText()).isEqualTo("report reused (window 30 d)");
        assertThat(count("bureau_pull")).isEqualTo(1);
    }

    @Test
    void addressMismatchIsDerivedFromTheFileAddress() throws Exception {
        String ssn = "937000009";
        Map<String, Object> body = defaultRequest();
        body.put("ssn", ssn);
        stubBureau(ssn, response("BP-NP01", "NEAR_PRIME", "14 Old Mill Rd, Dayton OH",
                5, 3, 0, "0.550", 40, 1997, false));

        MvcResult result = submit(USER, newKey(), body).andExpect(status().isAccepted()).andReturn();

        assertThat(read(result).get("engineInputPreview").get("addressMismatch").asBoolean()).isTrue();
    }

    @Test
    void bureauServerErrorYieldsBureauUnavailable() throws Exception {
        stubBureauServerError();

        MvcResult result = submit(USER, newKey(), defaultRequest())
                .andExpect(status().isAccepted())
                .andReturn();

        JsonNode body = read(result);
        assertThat(body.get("status").asText()).isEqualTo("BUREAU_UNAVAILABLE");
        assertThat(body.get("engineInputPreview").isNull()).isTrue();
        assertThat(step(body, "BUREAU").get("status").asText()).isEqualTo("WARN");
        assertThat(step(body, "BUREAU").get("detail").asText()).isEqualTo("bureau unavailable");
        assertThat(step(body, "FRAUD_SCREEN").get("status").asText()).isEqualTo("SKIPPED");
    }
}
