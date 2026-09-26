package com.parallax.application.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Demo users loaded from {@code parallax.users} (SPEC §9). Each user's password comes from an
 * environment variable ({@code passwordEnv}, default {@code DEMO_PASSWORD}); the resolution and
 * defaults live in {@link SecurityConfig}.
 */
@ConfigurationProperties(prefix = "parallax")
public class ParallaxUsersProperties {

    private List<User> users = new ArrayList<>();

    public List<User> getUsers() {
        return users;
    }

    public void setUsers(List<User> users) {
        this.users = users;
    }

    public static class User {
        private String username;
        private String displayName;
        private String role;
        private String passwordEnv;

        public String getUsername() {
            return username;
        }

        public void setUsername(String username) {
            this.username = username;
        }

        public String getDisplayName() {
            return displayName;
        }

        public void setDisplayName(String displayName) {
            this.displayName = displayName;
        }

        public String getRole() {
            return role;
        }

        public void setRole(String role) {
            this.role = role;
        }

        /**
         * The user's granted roles. Almost always a single role; a comma-separated {@code role}
         * (e.g. {@code STRATEGIST,APPROVER} for the test-only strat2 maker-checker user, SPEC §9)
         * grants each one. The first entry is the primary role used for PII masking ({@link CurrentUser}).
         */
        public List<String> getRoles() {
            if (role == null || role.isBlank()) {
                return List.of();
            }
            return Arrays.stream(role.split(","))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .toList();
        }

        public String getPasswordEnv() {
            return passwordEnv;
        }

        public void setPasswordEnv(String passwordEnv) {
            this.passwordEnv = passwordEnv;
        }
    }
}
