package com.parallax.application.decision;

import org.springframework.stereotype.Component;

/** Production no-op {@link CommitFaultHook}. A test bean (@Primary, profile test-fault) replaces it. */
@Component
public class NoopCommitFaultHook implements CommitFaultHook {

    @Override
    public void afterLedgerInsert() {
        // no-op
    }
}
