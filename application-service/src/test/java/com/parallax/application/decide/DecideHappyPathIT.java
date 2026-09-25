package com.parallax.application.decide;

import com.fasterxml.jackson.databind.JsonNode;
import com.parallax.application.intake.AbstractIntakeIT;
import org.junit.jupiter.api.Test;

import java.sql.Timestamp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class DecideHappyPathIT extends AbstractIntakeIT {

    private static final String USER = "priya.menon@parallax.dev";

    @Test
    void ishaanIsApprovedAndCommittedToTheLedger() throws Exception {
        stubBureau("912345678", primeResponse("BP-HAPPY", "48 Elm Street, Columbus OH"));

        JsonNode body = read(submit(USER, newKey(), defaultRequest()).andExpect(status().isCreated()).andReturn());

        assertThat(body.get("outcome").asText()).isEqualTo("APPROVED");
        assertThat(body.get("score").asInt()).isEqualTo(830);
        assertThat(body.get("creditLimit").asInt()).isEqualTo(12000);
        assertThat(body.get("ledgerSeq").asLong()).isPositive();
        assertThat(body.get("pipeline")).hasSize(6);
        body.get("pipeline").forEach(item -> assertThat(item.get("status").asText()).isEqualTo("OK"));

        assertThat(count("decision_ledger")).isEqualTo(1);

        JsonNode verify = read(getAs(USER, "/api/v1/ledger/verify").andReturn());
        assertThat(verify.get("ok").asBoolean()).isTrue();

        Timestamp firstUsed = jdbc.queryForObject(
                "SELECT first_used_at FROM rule_version WHERE version = 'v1.3'", Timestamp.class);
        assertThat(firstUsed).isNotNull();
    }
}
