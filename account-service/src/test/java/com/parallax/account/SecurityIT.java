package com.parallax.account;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Roles and the internal token (SPEC §9, §15): AUDITOR reads but cannot write; a wrong token is 401. */
class SecurityIT extends AbstractAccountsIT {

    @Test
    void auditorCanReadAccounts() throws Exception {
        getAs("sam.iyer@parallax.dev", "/api/v1/accounts").andExpect(status().isOk());
    }

    @Test
    void auditorCannotRequestACreditLineIncrease() throws Exception {
        postJsonAs("sam.iyer@parallax.dev", "/api/v1/accounts/ACC-88201/cli-requests",
                "{\"requestedLimit\":3000,\"acceptCounterOffer\":false}")
                .andExpect(status().isForbidden());
    }

    @Test
    void wrongInternalTokenIsUnauthorized() throws Exception {
        mvc.perform(post("/internal/v1/accounts").header("X-Internal-Token", "nope")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(openBody("APP-X", "Someone", "REWARDS_CARD", 2000, 64000, 1350, 280)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void missingInternalTokenIsUnauthorized() throws Exception {
        mvc.perform(post("/internal/v1/accounts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(openBody("APP-Y", "Someone", "REWARDS_CARD", 2000, 64000, 1350, 280)))
                .andExpect(status().isUnauthorized());
    }
}
