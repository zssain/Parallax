package com.parallax.application.drift;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/** Response DTO for the drift report (SPEC §11, §15). */
public final class DriftViews {

    private DriftViews() {
    }

    public record Report(Long id, Instant createdAt, LocalDate asOf, String liveVersion, int baselineN,
                         int currentN, List<Bin> bins, double psi, String status, boolean simulated) {
    }

    /** One score bin: the development baseline proportion, the current proportion and the PSI term. */
    public record Bin(int from, int to, double baseline, double current, double contribution) {
    }
}
