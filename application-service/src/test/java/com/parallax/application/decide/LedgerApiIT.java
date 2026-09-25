package com.parallax.application.decide;

import com.fasterxml.jackson.databind.JsonNode;
import com.parallax.application.intake.AbstractIntakeIT;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class LedgerApiIT extends AbstractIntakeIT {

    private static final String USER = "priya.menon@parallax.dev";

    @Test
    void statsAttemptUpdateAndTamperSimulation() throws Exception {
        for (String ssn : new String[]{"912345671", "912345672", "912345673"}) {
            stubBureau(ssn, primeResponse("BP-" + ssn, "48 Elm Street, Columbus OH"));
            Map<String, Object> body = defaultRequest();
            body.put("ssn", ssn);
            submit(USER, newKey(), body).andExpect(status().isCreated());
        }

        JsonNode stats = read(getAs(USER, "/api/v1/ledger/stats").andReturn());
        assertThat(stats.get("records").asLong()).isEqualTo(3);
        assertThat(stats.get("decisions").asLong()).isEqualTo(3);

        JsonNode update = read(postAs(USER, "/api/v1/ledger/demo/attempt-update").andReturn());
        assertThat(update.get("error").asText()).contains("permission denied");

        JsonNode tamper = read(postAs(USER, "/api/v1/ledger/demo/tamper-simulation").andReturn());
        assertThat(tamper.get("brokenAtSeq").asLong()).isEqualTo(tamper.get("modifiedSeq").asLong());

        JsonNode verify = read(getAs(USER, "/api/v1/ledger/verify").andReturn());
        assertThat(verify.get("ok").asBoolean()).isTrue(); // the real table is untouched
    }
}
