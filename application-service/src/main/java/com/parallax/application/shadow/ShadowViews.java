package com.parallax.application.shadow;

import java.util.List;

/** Response DTOs for shadow mode (SPEC §15). */
public final class ShadowViews {

    private ShadowViews() {
    }

    /** Result of toggling shadow for a version. */
    public record ShadowToggle(String version, boolean enabled) {
    }

    /** The shadow disagreements table for a version (newest first). */
    public record ShadowResults(long count, long disagreements, List<Item> items) {
    }

    public record Item(String applicationId, Side live, Side shadow, boolean agrees) {
    }

    public record Side(String outcome, Integer creditLimit) {
    }
}
