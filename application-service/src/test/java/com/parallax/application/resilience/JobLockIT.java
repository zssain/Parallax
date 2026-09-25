package com.parallax.application.resilience;

import com.parallax.application.intake.AbstractIntakeIT;
import com.parallax.application.jobs.RedecisionJob;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class JobLockIT extends AbstractIntakeIT {

    private static final String USER = "priya.menon@parallax.dev";

    @Autowired
    RedecisionJob redecisionJob;

    @Test
    void jobSkipsWhenTheAdvisoryLockIsHeld() throws Exception {
        // One application waiting to be re-decided.
        stubBureauServerError();
        submit(USER, newKey(), defaultRequest()).andExpect(status().isCreated());

        BUREAU.resetAll();
        stubBureau("912345678", referenceResponse("BP-LOCK", "912345678", "48 Elm Street, Columbus OH", 1996));
        bureauCircuit.close();

        String url = "jdbc:postgresql://" + POSTGRES.getHost() + ":" + POSTGRES.getMappedPort(5432) + "/parallax";
        try (Connection connection = DriverManager.getConnection(url, "parallax_app", "app-dev")) {
            try (Statement s = connection.createStatement()) {
                s.execute("SELECT pg_advisory_lock(727275)");
            }
            // The lock is held elsewhere → the job skips this run.
            assertThat(redecisionJob.runOnce()).isEmpty();
        }

        // Lock released → the job runs and re-decides.
        assertThat(redecisionJob.runOnce()).hasSize(1);
    }
}
