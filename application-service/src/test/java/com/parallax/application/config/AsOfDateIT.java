package com.parallax.application.config;

import com.parallax.application.intake.AbstractIntakeIT;
import com.parallax.application.ledger.LedgerEntry;
import com.parallax.application.ledger.LedgerKind;
import com.parallax.application.ledger.LedgerSource;
import com.parallax.application.ledger.LedgerWriter;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** AsOfDate with no property set: empty ledger → today; otherwise the newest ledger date (SPEC §2). */
class AsOfDateIT extends AbstractIntakeIT {

    @Autowired
    AsOfDate asOfDate;
    @Autowired
    LedgerWriter ledgerWriter;
    @Autowired
    PlatformTransactionManager txManager;

    @Test
    void emptyLedgerReturnsToday() {
        assertThat(asOfDate.get()).isEqualTo(LocalDate.now(ZoneOffset.UTC));
    }

    @Test
    void maxLedgerDateWins() {
        Instant when = Instant.parse("2026-05-20T10:00:00Z");
        new TransactionTemplate(txManager).executeWithoutResult(status ->
                ledgerWriter.appendAt(LedgerEntry.builder(LedgerKind.GOVERNANCE, LedgerSource.LIVE)
                        .governanceDetail(Map.of("note", "as-of test")).build(), when));

        assertThat(asOfDate.get()).isEqualTo(LocalDate.of(2026, 5, 20));
    }
}
