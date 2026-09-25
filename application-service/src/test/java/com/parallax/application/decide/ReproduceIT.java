package com.parallax.application.decide;

import com.fasterxml.jackson.databind.JsonNode;
import com.parallax.application.intake.AbstractIntakeIT;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ReproduceIT extends AbstractIntakeIT {

    private static final String USER = "priya.menon@parallax.dev";

    @Test
    void aDecisionReproducesIdentically() throws Exception {
        stubBureau("912345678", primeResponse("BP-REPRO", "48 Elm Street, Columbus OH"));
        JsonNode body = read(submit(USER, newKey(), defaultRequest()).andExpect(status().isCreated()).andReturn());
        long seq = body.get("ledgerSeq").asLong();

        JsonNode reproduce = read(getAs(USER, "/api/v1/decisions/" + seq + "/reproduce").andReturn());
        assertThat(reproduce.get("identical").asBoolean()).isTrue();
        assertThat(reproduce.get("stored").get("outcome").asText()).isEqualTo("APPROVED");
        assertThat(reproduce.get("recomputed").get("score").asInt()).isEqualTo(830);
    }
}
