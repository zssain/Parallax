package com.parallax.application.intake;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class IntakeVelocityIT extends AbstractIntakeIT {

    private static final String USER = "priya.menon@parallax.dev";

    @Test
    void threeApplicationsSharingAPhoneWithin24hReachVelocityThree() throws Exception {
        String phone = "5550100001";
        String[] ssns = {"912345671", "912345672", "912345673"};
        for (String ssn : ssns) {
            stubBureau(ssn, primeResponse("BP-" + ssn, "48 Elm Street, Columbus OH"));
        }

        JsonNode last = null;
        for (String ssn : ssns) {
            Map<String, Object> body = defaultRequest();
            body.put("ssn", ssn);
            body.put("phone", phone);
            last = read(submit(USER, newKey(), body).andExpect(status().isAccepted()).andReturn());
        }

        assertThat(last.get("engineInputPreview").get("velocity24h").asInt()).isEqualTo(3);
    }

    @Test
    void distinctApplicantsWithNoSharedContactHaveVelocityOne() throws Exception {
        String[] ssns = {"912345674", "912345675", "912345676"};
        for (String ssn : ssns) {
            stubBureau(ssn, primeResponse("BP-" + ssn, "48 Elm Street, Columbus OH"));
        }

        JsonNode last = null;
        for (String ssn : ssns) {
            Map<String, Object> body = defaultRequest();
            body.put("ssn", ssn); // no email, no phone
            last = read(submit(USER, newKey(), body).andExpect(status().isAccepted()).andReturn());
        }

        assertThat(last.get("engineInputPreview").get("velocity24h").asInt()).isEqualTo(1);
    }
}
