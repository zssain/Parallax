package com.parallax.application.domain;

import java.util.EnumMap;
import java.util.Map;
import java.util.Set;

/**
 * Enforces the application lifecycle of SPEC §6. Every status change goes through
 * {@link #transition(ApplicationStatus, ApplicationStatus)}; any transition not listed here throws.
 *
 * <pre>
 *   RECEIVED             → BUREAU_PULLED | BUREAU_UNAVAILABLE
 *   BUREAU_PULLED        → DECIDED | ENGINE_PENDING
 *   BUREAU_UNAVAILABLE   → DECIDED (automatic re-decision) | REVIEWED (REFER override)
 *   ENGINE_PENDING       → DECIDED | ENGINE_FAILED_MANUAL
 *   ENGINE_FAILED_MANUAL → REVIEWED (manual review)
 *   DECIDED              → REVIEWED (REFER override)
 * </pre>
 */
public final class ApplicationStateMachine {

    private static final Map<ApplicationStatus, Set<ApplicationStatus>> LEGAL =
            new EnumMap<>(ApplicationStatus.class);

    static {
        LEGAL.put(ApplicationStatus.RECEIVED,
                Set.of(ApplicationStatus.BUREAU_PULLED, ApplicationStatus.BUREAU_UNAVAILABLE));
        LEGAL.put(ApplicationStatus.BUREAU_PULLED,
                Set.of(ApplicationStatus.DECIDED, ApplicationStatus.ENGINE_PENDING));
        LEGAL.put(ApplicationStatus.BUREAU_UNAVAILABLE,
                Set.of(ApplicationStatus.DECIDED, ApplicationStatus.REVIEWED));
        LEGAL.put(ApplicationStatus.ENGINE_PENDING,
                Set.of(ApplicationStatus.DECIDED, ApplicationStatus.ENGINE_FAILED_MANUAL));
        LEGAL.put(ApplicationStatus.DECIDED,
                Set.of(ApplicationStatus.REVIEWED));
        LEGAL.put(ApplicationStatus.ENGINE_FAILED_MANUAL,
                Set.of(ApplicationStatus.REVIEWED));
        LEGAL.put(ApplicationStatus.REVIEWED, Set.of());
    }

    private ApplicationStateMachine() {
    }

    /** Returns {@code to} if the transition is legal (SPEC §6), else throws {@link IllegalStateException}. */
    public static ApplicationStatus transition(ApplicationStatus from, ApplicationStatus to) {
        if (from == null || to == null || !LEGAL.getOrDefault(from, Set.of()).contains(to)) {
            throw new IllegalStateException("Illegal application state transition: " + from + " → " + to);
        }
        return to;
    }
}
