package com.parallax.application.system;

import com.parallax.application.security.CurrentUser;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Service health, circuit breaker, queues and recent activity for the System screen (SPEC §15). */
@RestController
public class SystemController {

    private final SystemService systemService;
    private final CurrentUser currentUser;

    public SystemController(SystemService systemService, CurrentUser currentUser) {
        this.systemService = systemService;
        this.currentUser = currentUser;
    }

    @GetMapping("/api/v1/system/status")
    public SystemViews.Status status() {
        return systemService.status(currentUser.role());
    }

    @GetMapping("/api/v1/system/idempotency-keys")
    public List<SystemViews.IdempotencyKeyView> idempotencyKeys() {
        return systemService.idempotencyKeys();
    }

    @GetMapping("/api/v1/system/bureau-pulls")
    public List<SystemViews.BureauPullView> bureauPulls() {
        return systemService.bureauPulls();
    }
}
