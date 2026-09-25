package com.parallax.application.decision;

/** Extension point invoked right after the ledger insert; tests use it to prove commit atomicity. */
public interface CommitFaultHook {

    void afterLedgerInsert();
}
