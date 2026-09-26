package com.parallax.application.system;

import java.time.Instant;
import java.util.List;

/** Response DTOs for the System screen (SPEC §15). */
public final class SystemViews {

    private SystemViews() {
    }

    public record Status(String bureauCircuit, List<RedecisionItem> redecisionQueue, long enginePending,
                         long engineFailedManual, String liveVersion, List<Service> services) {
    }

    public record RedecisionItem(String applicationId, String displayName) {
    }

    public record Service(String name, String status, long latencyMs) {
    }

    public record BureauFaultResult(String mode, Integer delayMs, String circuit, List<String> redecided) {
    }

    public record IdempotencyKeyView(String key, String clientId, String state, String applicationId,
                                     Instant createdAt, Instant expiresAt) {
    }

    public record BureauPullView(String pullId, String pullType, String profile, String ssnLast4,
                                 Instant pulledAt) {
    }
}
