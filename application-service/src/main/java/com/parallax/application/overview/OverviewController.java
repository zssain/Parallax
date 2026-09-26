package com.parallax.application.overview;

import com.parallax.application.security.CurrentUser;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** The Overview landing screen endpoint (SPEC §15). */
@RestController
public class OverviewController {

    private final OverviewService overviewService;
    private final CurrentUser currentUser;

    public OverviewController(OverviewService overviewService, CurrentUser currentUser) {
        this.overviewService = overviewService;
        this.currentUser = currentUser;
    }

    @GetMapping("/api/v1/overview")
    public OverviewViews.Overview overview() {
        return overviewService.overview(currentUser.role());
    }
}
