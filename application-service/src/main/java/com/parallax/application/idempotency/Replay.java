package com.parallax.application.idempotency;

/** A stored idempotent response to replay (SPEC §7): the original HTTP status and JSON body. */
public record Replay(int status, String body) {
}
