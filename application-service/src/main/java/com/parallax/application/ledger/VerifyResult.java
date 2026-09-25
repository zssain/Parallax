package com.parallax.application.ledger;

/** The outcome of a ledger chain verification (SPEC §15): whether it holds, how many rows, and where it broke. */
public record VerifyResult(boolean ok, int checked, Long brokenAtSeq, long ms) {
}
