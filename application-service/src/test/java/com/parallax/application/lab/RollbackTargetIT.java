package com.parallax.application.lab;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** Before any promotion, the rollback target is the seeded RETIRED v1.2 (SPEC §10, §15). */
class RollbackTargetIT extends AbstractLabIT {

    @Test
    void initialRollbackTargetIsV12() throws Exception {
        JsonNode body = read(getAs(STRATEGIST, "/api/v1/lab/versions").andReturn());

        assertThat(body.get("liveVersion").asText()).isEqualTo("v1.3");
        assertThat(body.get("rollbackTarget").asText()).isEqualTo("v1.2");
    }
}
