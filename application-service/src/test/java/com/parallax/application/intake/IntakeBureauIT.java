package com.parallax.application.intake;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;

import static com.github.tomakehurst.wiremock.client.WireMock.containing;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class IntakeBureauIT extends AbstractIntakeIT {

    private static final String USER = "priya.menon@parallax.dev";
    private static final String PRIME_SSN = "912345678";

    @Test
    void happyPathDecidesAndReturns201WithSixPipelineSteps() throws Exception {
        stubBureau(PRIME_SSN, primeResponse("BP-PRIME01", "48 Elm Street, Columbus OH"));

        MvcResult result = submit(USER, newKey(), defaultRequest())
                .andExpect(status().isCreated())
                .andReturn();

        JsonNode body = read(result);
        assertThat(body.get("status").asText()).isEqualTo("DECIDED");
        assertThat(body.get("outcome").asText()).isEqualTo("APPROVED");
        assertThat(body.get("score").asInt()).isEqualTo(830);
        assertThat(body.get("creditLimit").asInt()).isEqualTo(12000);
        assertThat(body.get("ledgerSeq").asLong()).isPositive();
        assertThat(body.get("bureau").get("pullId").asText()).isEqualTo("BP-PRIME01");
        assertThat(stepNames(body)).containsExactly(
                "VALIDATE", "IDEMPOTENCY", "BUREAU", "FRAUD_SCREEN", "ENGINE", "LEDGER_COMMIT");
        assertThat(step(body, "ENGINE").get("label").asText()).isEqualTo("Decision engine · v1.3");
        assertThat(step(body, "LEDGER_COMMIT").get("detail").asText()).startsWith("seq #");
        assertThat(count("application")).isEqualTo(1);
        assertThat(count("decision_ledger")).isEqualTo(1);
    }

    @Test
    void secondApplicationReusesTheReportWithinTheWindow() throws Exception {
        stubBureau(PRIME_SSN, primeResponse("BP-PRIME01", "48 Elm Street, Columbus OH"));

        submit(USER, newKey(), defaultRequest()).andExpect(status().isCreated());
        MvcResult second = submit(USER, newKey(), defaultRequest())
                .andExpect(status().isCreated())
                .andReturn();

        BUREAU.verify(1, postRequestedFor(urlEqualTo("/ws")).withRequestBody(containing(PRIME_SSN)));
        assertThat(step(read(second), "BUREAU").get("detail").asText()).isEqualTo("report reused (window 30 d)");
    }

    @Test
    void bureauServerErrorYieldsBureauUnavailableRefer() throws Exception {
        stubBureauServerError();

        MvcResult result = submit(USER, newKey(), defaultRequest())
                .andExpect(status().isCreated())
                .andReturn();

        JsonNode body = read(result);
        assertThat(body.get("status").asText()).isEqualTo("BUREAU_UNAVAILABLE");
        assertThat(body.get("outcome").asText()).isEqualTo("REFER");
        assertThat(body.get("reasonCodes")).isEmpty(); // B01 is internal
        assertThat(step(body, "BUREAU").get("detail").asText()).isEqualTo("bureau unavailable");
        assertThat(step(body, "FRAUD_SCREEN").get("status").asText()).isEqualTo("SKIPPED");
        assertThat(step(body, "ENGINE").get("status").asText()).isEqualTo("SKIPPED");

        String reasonCodes = jdbc.queryForObject(
                "SELECT reason_codes::text FROM decision_ledger ORDER BY seq DESC LIMIT 1", String.class);
        assertThat(reasonCodes).contains("B01");
    }
}
