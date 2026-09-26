package com.parallax.application.lab;

import java.time.Instant;

/** Body of {@code POST /api/v1/lab/versions/{v}/replays} (SPEC §15): optional created_at window. */
public record ReplayRequest(Instant from, Instant to) {
}
