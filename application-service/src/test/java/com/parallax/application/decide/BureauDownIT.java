package com.parallax.application.decide;

import com.fasterxml.jackson.databind.JsonNode;
import com.parallax.application.intake.AbstractIntakeIT;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class BureauDownIT extends AbstractIntakeIT {

    private static final String USER = "priya.menon@parallax.dev";

    @Test
    void bureauOutageBecomesB01ReferAndReproducesToNull() throws Exception {
        stubBureauServerError();

        JsonNode body = read(submit(USER, newKey(), defaultRequest()).andExpect(status().isCreated()).andReturn());
        assertThat(body.get("status").asText()).isEqualTo("BUREAU_UNAVAILABLE");
        assertThat(body.get("outcome").asText()).isEqualTo("REFER");
        assertThat(body.get("reasonCodes")).isEmpty();

        String ledgerReasons = jdbc.queryForObject(
                "SELECT reason_codes::text FROM decision_ledger ORDER BY seq DESC LIMIT 1", String.class);
        assertThat(ledgerReasons).contains("B01");

        long seq = body.get("ledgerSeq").asLong();
        JsonNode reproduce = read(getAs(USER, "/api/v1/decisions/" + seq + "/reproduce").andReturn());
        assertThat(reproduce.get("identical").isNull()).isTrue();
        assertThat(reproduce.get("message").asText()).isEqualTo("No engine evaluation (bureau unavailable)");
    }
}
