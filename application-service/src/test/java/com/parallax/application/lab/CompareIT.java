package com.parallax.application.lab;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** v1.2 vs v1.3 differ only in approveCutoff and utilPts[1..4] (SPEC §4, §15). */
class CompareIT extends AbstractLabIT {

    private static final String INTERNAL = "priya.menon@parallax.dev";

    @Test
    void v12VsV13DiffersOnlyInCutoffAndUtilPoints() throws Exception {
        JsonNode body = read(getAs(INTERNAL, "/api/v1/lab/versions/compare?a=v1.2&b=v1.3").andReturn());

        assertThat(body.get("a").asText()).isEqualTo("v1.2");
        assertThat(body.get("b").asText()).isEqualTo("v1.3");

        List<String> fields = new ArrayList<>();
        for (JsonNode d : body.get("differences")) {
            fields.add(d.get("field").asText());
        }
        assertThat(fields).containsExactly(
                "approveCutoff", "utilPts[1]", "utilPts[2]", "utilPts[3]", "utilPts[4]");

        JsonNode cutoff = body.get("differences").get(0);
        assertThat(cutoff.get("a").asInt()).isEqualTo(670);
        assertThat(cutoff.get("b").asInt()).isEqualTo(680);
    }
}
