package com.parallax.bureau;

import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Dev-only fault injection endpoint at /admin/fault (SPEC §8, resilience demo). */
@RestController
@Profile("dev")
@RequestMapping("/admin/fault")
public class FaultController {

    private final FaultStore store;

    public FaultController(FaultStore store) {
        this.store = store;
    }

    @GetMapping
    public FaultState current() {
        return store.get();
    }

    @PostMapping
    public FaultState set(@RequestBody FaultState request) {
        String mode = request.mode() == null ? "NONE" : request.mode();
        store.set(new FaultState(mode, request.delayMs()));
        return store.get();
    }
}
