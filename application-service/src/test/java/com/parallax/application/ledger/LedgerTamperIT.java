package com.parallax.application.ledger;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LedgerTamperIT extends AbstractLedgerIT {

    @Test
    void tamperingWithARowIsDetectedAtThatSeq() {
        appendInTx(decisionEntry(insertApplication("APP-T1"), "APP-T1"));
        LedgerRecord second = appendInTx(decisionEntry(insertApplication("APP-T2"), "APP-T2"));

        // Only parallax_owner can mutate the append-only table; simulate a rogue edit.
        ownerJdbc.update("UPDATE decision_ledger SET credit_limit = 25000 WHERE seq = ?", second.seq());

        VerifyResult result = ledgerVerifier.verify();
        assertThat(result.ok()).isFalse();
        assertThat(result.brokenAtSeq()).isEqualTo(second.seq());
    }
}
