package com.parallax.application.decide;

import com.fasterxml.jackson.databind.JsonNode;
import com.parallax.application.intake.AbstractIntakeIT;
import com.parallax.application.pii.NameMasker;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class MaskingIT extends AbstractIntakeIT {

    private static final String UNDERWRITER = "priya.menon@parallax.dev";
    private static final String AUDITOR = "sam.iyer@parallax.dev";

    @Test
    void underwriterSeesFullNamesAuditorSeesMaskedAndNoAddress() throws Exception {
        stubBureau("912345678", primeResponse("BP-MASK", "48 Elm Street, Columbus OH"));
        JsonNode created = read(submit(UNDERWRITER, newKey(), defaultRequest())
                .andExpect(status().isCreated()).andReturn());
        String applicationId = created.get("applicationId").asText();

        JsonNode underwriterDetail = read(getAs(UNDERWRITER, "/api/v1/applications/" + applicationId).andReturn());
        assertThat(underwriterDetail.get("displayName").asText()).isEqualTo("Ishaan Kapoor");
        assertThat(underwriterDetail.get("address").asText()).isEqualTo("48 Elm Street, Columbus OH");

        JsonNode auditorDetail = read(getAs(AUDITOR, "/api/v1/applications/" + applicationId).andReturn());
        assertThat(auditorDetail.get("displayName").asText()).isEqualTo(NameMasker.mask("Ishaan Kapoor"));
        assertThat(auditorDetail.get("address").isNull()).isTrue();

        JsonNode auditorList = read(getAs(AUDITOR, "/api/v1/applications").andReturn());
        assertThat(auditorList.get("items").get(0).get("displayName").asText())
                .isEqualTo(NameMasker.mask("Ishaan Kapoor"));
    }
}
