package com.parallax.application.decide;

import com.parallax.application.decision.CommitFaultHook;
import com.parallax.application.intake.AbstractIntakeIT;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Import(AtomicityIT.FaultConfig.class)
class AtomicityIT extends AbstractIntakeIT {

    private static final String USER = "priya.menon@parallax.dev";

    /** A test bean (@Primary) that throws right after the ledger insert to prove commit atomicity. */
    @TestConfiguration
    static class FaultConfig {
        @Bean
        @Primary
        CommitFaultHook faultyCommitHook() {
            return () -> {
                throw new IllegalStateException("boom after ledger insert");
            };
        }
    }

    @Test
    void anExceptionAfterTheLedgerInsertRollsBackEverything() throws Exception {
        stubBureau("912345678", primeResponse("BP-ATOMIC", "48 Elm Street, Columbus OH"));
        String key = newKey();

        submit(USER, key, defaultRequest()).andExpect(status().is5xxServerError());

        assertThat(count("decision_ledger")).isZero();
        String status = jdbc.queryForObject(
                "SELECT status FROM application ORDER BY id DESC LIMIT 1", String.class);
        assertThat(status).isNotEqualTo("DECIDED");
        Integer keys = jdbc.queryForObject(
                "SELECT count(*) FROM idempotency_key WHERE idem_key = ?", Integer.class, key);
        assertThat(keys).isZero();
    }
}
