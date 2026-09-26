package com.parallax.application.seed;

import com.parallax.application.intake.AbstractIntakeIT;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

/** History ingest: 2,000 SEED applications, matching loan outcomes, a verified chain, refusal on re-run. */
class HistorySeederIT extends AbstractIntakeIT {

    @Autowired
    HistorySeeder historySeeder;

    @DynamicPropertySource
    static void seedProps(DynamicPropertyRegistry registry) {
        registry.add("parallax.seed.generate-count", () -> "2000");
    }

    @Test
    void seedsHistoryThenRefusesASecondRun() {
        HistorySeeder.SeedSummary first = historySeeder.seed();

        assertThat(first.refused()).isFalse();
        assertThat(first.count()).isEqualTo(2000);
        assertThat(count("application")).isEqualTo(2000);
        assertThat(count("decision_ledger")).isEqualTo(2000);
        assertThat(count("loan_outcome")).isEqualTo(2000);

        int approved = jdbc.queryForObject(
                "SELECT count(*) FROM decision_ledger WHERE source = 'SEED' AND outcome = 'APPROVED'", Integer.class);
        int observed = jdbc.queryForObject(
                "SELECT count(*) FROM loan_outcome WHERE simulated = false", Integer.class);
        assertThat(observed).isEqualTo(approved);

        // Every SEED row uses SEED source and carries no bureau pull; the chain verifies.
        assertThat(count("decision_ledger WHERE source = 'SEED' AND kind = 'DECISION'")).isEqualTo(2000);
        assertThat(first.verify().ok()).isTrue();
        assertThat(first.verify().checked()).isEqualTo(2000);

        // v1.3 first_used_at is now stamped.
        Integer stamped = jdbc.queryForObject(
                "SELECT count(*) FROM rule_version WHERE version = 'v1.3' AND first_used_at IS NOT NULL", Integer.class);
        assertThat(stamped).isEqualTo(1);

        // The ledger is no longer empty → a second run is refused with the exact message.
        HistorySeeder.SeedSummary second = historySeeder.seed();
        assertThat(second.refused()).isTrue();
        assertThat(second.message()).isEqualTo(HistorySeeder.REFUSED_MESSAGE);
        assertThat(count("application")).isEqualTo(2000); // nothing was added or deleted
    }
}
