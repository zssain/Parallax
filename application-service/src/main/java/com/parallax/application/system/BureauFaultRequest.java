package com.parallax.application.system;

/** Body of the dev-only {@code POST /api/v1/system/bureau-fault} (SPEC §15). */
public record BureauFaultRequest(String mode, Integer delayMs) {
}
