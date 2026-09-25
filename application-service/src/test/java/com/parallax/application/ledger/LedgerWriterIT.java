package com.parallax.application.ledger;

import org.junit.jupiter.api.Test;
import org.springframework.transaction.IllegalTransactionStateException;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LedgerWriterIT extends AbstractLedgerIT {

    @Test
    void appendOutsideATransactionIsRejected() {
        LedgerEntry entry = decisionEntry(insertApplication("APP-NOTX"), "APP-NOTX");
        // MANDATORY propagation: no active transaction → refuse.
        assertThatThrownBy(() -> ledgerWriter.append(entry))
                .isInstanceOf(IllegalTransactionStateException.class);
    }
}
