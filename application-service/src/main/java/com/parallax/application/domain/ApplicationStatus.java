package com.parallax.application.domain;

/** Lifecycle states of an application (SPEC §6). Transitions are enforced by {@link ApplicationStateMachine}. */
public enum ApplicationStatus {
    RECEIVED,
    BUREAU_PULLED,
    BUREAU_UNAVAILABLE,
    ENGINE_PENDING,
    ENGINE_FAILED_MANUAL,
    DECIDED,
    REVIEWED
}
