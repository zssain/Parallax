package com.parallax.application.web;

import com.parallax.application.security.CurrentUser;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** {@code GET /api/v1/me} — the identity of the authenticated caller (SPEC §15). */
@RestController
public class MeController {

    private final CurrentUser currentUser;

    public MeController(CurrentUser currentUser) {
        this.currentUser = currentUser;
    }

    @GetMapping("/api/v1/me")
    public MeResponse me() {
        return new MeResponse(currentUser.username(), currentUser.displayName(), currentUser.role());
    }

    public record MeResponse(String username, String displayName, String role) {
    }
}
