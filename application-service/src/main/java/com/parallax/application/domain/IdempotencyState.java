package com.parallax.application.domain;

/** Idempotency key lifecycle (SPEC §7). */
public enum IdempotencyState {
    IN_PROGRESS,
    COMPLETED
}
