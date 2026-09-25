package com.parallax.application.ledger;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LedgerChainIT extends AbstractLedgerIT {

    private static final String GENESIS = "0".repeat(64);

    @Test
    void chainLinksEachRowToThePrevious() {
        LedgerRecord r1 = appendInTx(decisionEntry(insertApplication("APP-1"), "APP-1"));
        LedgerRecord r2 = appendInTx(decisionEntry(insertApplication("APP-2"), "APP-2"));
        LedgerRecord r3 = appendInTx(decisionEntry(insertApplication("APP-3"), "APP-3"));

        assertThat(r1.seq()).isEqualTo(1);
        assertThat(r2.seq()).isEqualTo(2);
        assertThat(r3.seq()).isEqualTo(3);
        assertThat(r1.prevHash()).isEqualTo(GENESIS);
        assertThat(r2.prevHash()).isEqualTo(r1.hash());
        assertThat(r3.prevHash()).isEqualTo(r2.hash());

        VerifyResult result = ledgerVerifier.verify();
        assertThat(result.ok()).isTrue();
        assertThat(result.checked()).isEqualTo(3);
        assertThat(result.brokenAtSeq()).isNull();
    }
}
