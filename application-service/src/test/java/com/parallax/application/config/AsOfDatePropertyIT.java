package com.parallax.application.config;

import com.parallax.application.AbstractPostgresIT;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/** AsOfDate with parallax.as-of set: the configured date wins over the ledger (SPEC §2). */
@TestPropertySource(properties = "parallax.as-of=2026-03-10")
class AsOfDatePropertyIT extends AbstractPostgresIT {

    @Autowired
    AsOfDate asOfDate;

    @Test
    void configuredDateWins() {
        assertThat(asOfDate.get()).isEqualTo(LocalDate.of(2026, 3, 10));
    }
}
