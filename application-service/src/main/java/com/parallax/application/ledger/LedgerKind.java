package com.parallax.application.ledger;

/** The kinds of decision-ledger rows (SPEC §5). */
public enum LedgerKind {
    DECISION,
    REDECISION,
    OVERRIDE,
    GOVERNANCE
}
