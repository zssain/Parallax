package com.parallax.application.query;

import org.springframework.stereotype.Component;

import java.util.Set;

/** Role-based PII visibility (SPEC §9): full names for underwriting roles; AUDITOR/ASSISTANT see masked. */
@Component
public class DisplayNamePolicy {

    private static final Set<String> FULL_NAME_ROLES = Set.of("UNDERWRITER", "STRATEGIST", "APPROVER");

    public boolean fullName(String role) {
        return FULL_NAME_ROLES.contains(role);
    }

    /** AUDITOR never sees addresses (SPEC §9); everyone else with access does. */
    public boolean addressVisible(String role) {
        return !"AUDITOR".equals(role);
    }
}
