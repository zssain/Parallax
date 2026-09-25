package com.parallax.application.decide;

import com.fasterxml.jackson.databind.JsonNode;
import com.parallax.application.intake.AbstractIntakeIT;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class DeclineIT extends AbstractIntakeIT {

    private static final String USER = "priya.menon@parallax.dev";

    @Test
    void priyaIsDeclinedWithAdverseActionNotice() throws Exception {
        String ssn = "961234567"; // SUBPRIME / NONE
        stubBureau(ssn, referenceResponse("BP-PRIYA", ssn, "48 Elm Street, Columbus OH", 1995));

        Map<String, Object> priya = defaultRequest();
        priya.put("firstName", "Priya");
        priya.put("lastName", "Sharma");
        priya.put("dateOfBirth", "1995-04-18");
        priya.put("ssn", ssn);
        priya.put("annualIncome", 38000);
        priya.put("monthlyHousing", 1300);
        priya.put("monthlyDebt", 520);

        JsonNode body = read(submit(USER, newKey(), priya).andExpect(status().isCreated()).andReturn());
        assertThat(body.get("outcome").asText()).isEqualTo("DECLINED");
        assertThat(body.get("score").asInt()).isEqualTo(445);

        List<String> codes = new ArrayList<>();
        body.get("reasonCodes").forEach(rc -> codes.add(rc.get("code").asText()));
        assertThat(codes).containsExactly("R22", "R31", "R14", "R05");

        String applicationId = body.get("applicationId").asText();
        String notice = getAs(USER, "/api/v1/applications/" + applicationId + "/adverse-action-notice")
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(notice)
                .contains("Delinquency on one or more accounts in the past 24 months")
                .contains("Proportion of revolving balances to credit limits is too high")
                .contains("Too many recent inquiries for credit")
                .contains("Length of credit history is too short")
                .contains("Template AAN-v2")
                .doesNotContain("F0");
    }
}
