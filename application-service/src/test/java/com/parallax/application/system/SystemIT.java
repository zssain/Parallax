package com.parallax.application.system;

import com.fasterxml.jackson.databind.JsonNode;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.parallax.application.intake.AbstractIntakeIT;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** System status, health probes and the dev bureau fault switch (SPEC §15). */
class SystemIT extends AbstractIntakeIT {

    private static final String UW = "priya.menon@parallax.dev";

    /** Pin decision- and assistant-service at a closed port so the probes are deterministically DOWN. */
    @DynamicPropertySource
    static void downServices(DynamicPropertyRegistry registry) {
        registry.add("parallax.decision.url", () -> "http://localhost:1");
        registry.add("parallax.assistant.url", () -> "http://localhost:1");
    }

    @Test
    void statusListsFiveServicesWithExactNames() throws Exception {
        JsonNode status = read(getAs(UW, "/api/v1/system/status").andExpect(status().isOk()).andReturn());
        assertThat(status.get("liveVersion").asText()).isEqualTo("v1.3");
        assertThat(status.get("bureauCircuit").asText()).isEqualTo("CLOSED");

        JsonNode services = status.get("services");
        List<String> names = new ArrayList<>();
        services.forEach(s -> names.add(s.get("name").asText()));
        assertThat(names).containsExactly("application-service", "decision-service", "bureau-mock (SOAP)",
                "assistant-service", "postgres");

        assertThat(serviceStatus(services, "application-service")).isEqualTo("UP");
        assertThat(serviceStatus(services, "postgres")).isEqualTo("UP");
        assertThat(serviceStatus(services, "decision-service")).isEqualTo("DOWN");
        assertThat(serviceStatus(services, "bureau-mock (SOAP)")).isEqualTo("DOWN");
        assertThat(serviceStatus(services, "assistant-service")).isEqualTo("DOWN");
    }

    @Test
    void bureauFaultDownStrandsThenNoneRecovers() throws Exception {
        stubAdminFault();

        // DOWN forces the circuit open; the next application falls back to a B01 REFER.
        JsonNode down = read(postJsonAs(UW, "/api/v1/system/bureau-fault", json(Map.of("mode", "DOWN")))
                .andExpect(status().isOk()).andReturn());
        assertThat(down.get("circuit").asText()).isEqualTo("OPEN");

        Map<String, Object> body = defaultRequest();
        body.put("ssn", "912345678");
        JsonNode stranded = read(submit(UW, newKey(), body).andExpect(status().isCreated()).andReturn());
        assertThat(stranded.get("status").asText()).isEqualTo("BUREAU_UNAVAILABLE");
        assertThat(stranded.get("outcome").asText()).isEqualTo("REFER");
        String appId = stranded.get("applicationId").asText();

        // NONE closes the circuit and re-decides the stranded application synchronously.
        BUREAU.resetAll();
        stubAdminFault();
        stubBureau("912345678", referenceResponse("BP-RECOVER", "912345678", "48 Elm Street, Columbus OH", 1996));
        JsonNode none = read(postJsonAs(UW, "/api/v1/system/bureau-fault", json(Map.of("mode", "NONE")))
                .andExpect(status().isOk()).andReturn());
        assertThat(none.get("circuit").asText()).isEqualTo("CLOSED");

        List<String> redecided = new ArrayList<>();
        none.get("redecided").forEach(id -> redecided.add(id.asText()));
        assertThat(redecided).contains(appId);

        assertThat(jdbc.queryForObject("SELECT status FROM application WHERE public_id = ?", String.class, appId))
                .isEqualTo("DECIDED");
    }

    private void stubAdminFault() {
        BUREAU.stubFor(WireMock.post(urlEqualTo("/admin/fault")).willReturn(aResponse()
                .withStatus(200).withHeader("Content-Type", "application/json")
                .withBody("{\"mode\":\"NONE\",\"delayMs\":0}")));
    }

    private String serviceStatus(JsonNode services, String name) {
        for (JsonNode s : services) {
            if (s.get("name").asText().equals(name)) {
                return s.get("status").asText();
            }
        }
        throw new AssertionError("no service named " + name);
    }
}
