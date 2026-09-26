package com.parallax.application.system;

import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * Dev-only fault switch (SPEC §15): pushes a fault to bureau-mock and aligns the local circuit so the
 * resilience story can be demonstrated. Registered only under the {@code dev} profile, mirroring
 * bureau-mock's own {@code /admin/fault}.
 */
@RestController
@Profile("dev")
public class BureauFaultController {

    private final SystemService systemService;

    public BureauFaultController(SystemService systemService) {
        this.systemService = systemService;
    }

    @PostMapping("/api/v1/system/bureau-fault")
    public SystemViews.BureauFaultResult bureauFault(@RequestBody BureauFaultRequest request) {
        return systemService.bureauFault(request);
    }
}
