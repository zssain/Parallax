package com.parallax.application.drift;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** Drift endpoints (SPEC §15): the latest stored report, an on-demand run and a market-shift simulation. */
@RestController
public class DriftController {

    private final DriftService driftService;

    public DriftController(DriftService driftService) {
        this.driftService = driftService;
    }

    @GetMapping("/api/v1/drift/latest")
    public DriftViews.Report latest() {
        return driftService.latest();
    }

    @PostMapping("/api/v1/drift/run")
    public DriftViews.Report run() {
        return driftService.run();
    }

    @PostMapping("/api/v1/drift/simulate")
    public DriftViews.Report simulate(@RequestBody Map<String, Double> body) {
        Double shift = body.get("shift");
        return driftService.simulate(shift == null ? 0.0 : shift);
    }
}
