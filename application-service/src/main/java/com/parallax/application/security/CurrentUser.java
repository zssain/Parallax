package com.parallax.application.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Convenience accessor for the authenticated caller's identity (SPEC §15 {@code GET /api/v1/me}):
 * username, display name and role. Username comes from the SecurityContext; display name and role are
 * looked up from {@link ParallaxUsersProperties} by username, so it does not depend on the concrete
 * {@code UserDetails} implementation (InMemoryUserDetailsManager returns a plain {@code User}).
 */
@Component
public class CurrentUser {

    private final Map<String, ParallaxUsersProperties.User> byUsername = new LinkedHashMap<>();

    public CurrentUser(ParallaxUsersProperties properties) {
        for (ParallaxUsersProperties.User u : properties.getUsers()) {
            byUsername.put(u.getUsername().toLowerCase(), u);
        }
    }

    public String username() {
        return authentication().getName();
    }

    public String displayName() {
        return lookup().getDisplayName();
    }

    /** The caller's primary role (the first of possibly several), used for PII masking (SPEC §9). */
    public String role() {
        java.util.List<String> roles = lookup().getRoles();
        return roles.isEmpty() ? null : roles.get(0);
    }

    private ParallaxUsersProperties.User lookup() {
        String username = authentication().getName();
        ParallaxUsersProperties.User user = byUsername.get(username.toLowerCase());
        if (user == null) {
            throw new IllegalStateException("Authenticated user is not a configured Parallax user: " + username);
        }
        return user;
    }

    private Authentication authentication() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            throw new IllegalStateException("No authenticated user in the security context");
        }
        return auth;
    }
}
