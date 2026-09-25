package com.parallax.application.ledger;

/** The source of a decision-ledger row (SPEC §5): live traffic or seeded synthetic history. */
public enum LedgerSource {
    LIVE,
    SEED
}
