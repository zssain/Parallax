package com.parallax.application.resilience;

import com.fasterxml.jackson.databind.JsonNode;
import com.parallax.application.intake.AbstractIntakeIT;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class CircuitBreakerIT extends AbstractIntakeIT {

    private static final String USER = "priya.menon@parallax.dev";

    @Test
    void breakerOpensAfterFiveFailuresAndBlocksTheSixthCallWithoutSoap() throws Exception {
        stubBureauServerError();

        for (int i = 0; i < 5; i++) {
            Map<String, Object> body = defaultRequest();
            body.put("ssn", "91234560" + i);
            submit(USER, newKey(), body).andExpect(status().isCreated());
        }

        Map<String, Object> sixth = defaultRequest();
        sixth.put("ssn", "912345699");
        JsonNode body = read(submit(USER, newKey(), sixth).andExpect(status().isCreated()).andReturn());

        assertThat(body.get("status").asText()).isEqualTo("BUREAU_UNAVAILABLE");
        assertThat(step(body, "BUREAU").get("detail").asText()).isEqualTo("circuit OPEN");
        // Exactly five SOAP calls: the sixth was blocked by the open circuit.
        BUREAU.verify(5, postRequestedFor(urlEqualTo("/ws")));
    }
}
