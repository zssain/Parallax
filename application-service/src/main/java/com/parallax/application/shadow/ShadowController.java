package com.parallax.application.shadow;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** Shadow-mode endpoints (SPEC §15): toggle a version into shadow and read its disagreements. */
@RestController
public class ShadowController {

    private final ShadowService shadowService;

    public ShadowController(ShadowService shadowService) {
        this.shadowService = shadowService;
    }

    @PostMapping("/api/v1/lab/versions/{v}/shadow")
    public ShadowViews.ShadowToggle setShadow(@PathVariable String v, @RequestBody Map<String, Boolean> body) {
        boolean enabled = Boolean.TRUE.equals(body.get("enabled"));
        return shadowService.setShadow(v, enabled);
    }

    @GetMapping("/api/v1/lab/versions/{v}/shadow-results")
    public ShadowViews.ShadowResults results(@PathVariable String v) {
        return shadowService.results(v);
    }
}
