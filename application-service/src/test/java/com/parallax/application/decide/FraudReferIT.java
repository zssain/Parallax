package com.parallax.application.decide;

import com.fasterxml.jackson.databind.JsonNode;
import com.parallax.application.intake.AbstractIntakeIT;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class FraudReferIT extends AbstractIntakeIT {

    private static final String USER = "priya.menon@parallax.dev";

    @Test
    void addressMismatchRefersWithFraudFlagHiddenFromTheClient() throws Exception {
        String ssn = "937123456"; // NEAR_PRIME / ADDRESS_MISMATCH
        stubBureau(ssn, referenceResponse("BP-SARA", ssn, "48 Elm Street, Columbus OH", 1998));

        Map<String, Object> sara = defaultRequest();
        sara.put("firstName", "Sara");
        sara.put("lastName", "Khan");
        sara.put("dateOfBirth", "1998-04-18");
        sara.put("ssn", ssn);
        sara.put("annualIncome", 45000);
        sara.put("monthlyHousing", 1200);
        sara.put("monthlyDebt", 300);

        JsonNode body = read(submit(USER, newKey(), sara).andExpect(status().isCreated()).andReturn());
        assertThat(body.get("outcome").asText()).isEqualTo("REFER");
        assertThat(body.toString()).doesNotContain("F01");
        assertThat(step(body, "FRAUD_SCREEN").get("status").asText()).isEqualTo("WARN");
        assertThat(step(body, "FRAUD_SCREEN").get("detail").asText()).isEqualTo("1 flag(s)");

        String applicationId = body.get("applicationId").asText();
        JsonNode detail = read(getAs(USER, "/api/v1/applications/" + applicationId).andReturn());
        assertThat(detail.get("base").get("fraudFlags").get(0).get("code").asText()).isEqualTo("F01");
    }
}
