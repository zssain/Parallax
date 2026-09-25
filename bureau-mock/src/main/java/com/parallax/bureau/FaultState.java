package com.parallax.bureau;

/** Injected fault configuration for the resilience demo. mode is NONE, DOWN or SLOW. */
public record FaultState(String mode, int delayMs) {
}
