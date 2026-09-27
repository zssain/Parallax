package com.parallax.account;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Opening an account is idempotent by applicationId (SPEC §13): first 201, then 200, one row. */
class OpenAccountIT extends AbstractAccountsIT {

    @Test
    void openingTwiceWithTheSameApplicationIdIsIdempotent() throws Exception {
        String body = openBody("APP-1042", "Arjun Mehta", "STORE_CARD", 12000, 96000, 1800, 300);

        mvc.perform(post("/internal/v1/accounts").header("X-Internal-Token", INTERNAL_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.accountId").value("ACC-88201"))
                .andExpect(jsonPath("$.applicationId").value("APP-1042"))
                .andExpect(jsonPath("$.creditLimit").value(12000))
                .andExpect(jsonPath("$.status").value("CURRENT"));

        mvc.perform(post("/internal/v1/accounts").header("X-Internal-Token", INTERNAL_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accountId").value("ACC-88201"));

        Integer rows = jdbc.queryForObject("SELECT count(*) FROM account", Integer.class);
        assertThat(rows).isEqualTo(1);
    }
}
