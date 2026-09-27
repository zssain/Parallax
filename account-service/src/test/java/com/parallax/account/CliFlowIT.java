package com.parallax.account;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** The credit-line-increase flow (SPEC §13) end to end against real statements. */
class CliFlowIT extends AbstractAccountsIT {

    private static final String UNDERWRITER = "priya.menon@parallax.dev";

    @Test
    void sixOnTimeMonthsQualifyForA3000Increase() throws Exception {
        // Ishaan's financials → atpMax 29200 under LIVE v1.3; open at limit 2000.
        JsonNode account = openAccount(openBody("APP-ISHAAN", "Ishaan Kapoor", "REWARDS_CARD",
                2000, 64000, 1350, 280));
        String id = account.get("accountId").asText();

        long payment = 0; // month 1 pays nothing; thereafter pay the previous statement's minimum, on time.
        for (int month = 1; month <= 6; month++) {
            simulateMonth(UNDERWRITER, id, 20000, payment, true).andExpect(status().isOk());
            payment = newestMinimumDue(id);
        }

        JsonNode result = read(postJsonAs(UNDERWRITER, "/api/v1/accounts/" + id + "/cli-requests",
                "{\"requestedLimit\":3000,\"acceptCounterOffer\":false}").andExpect(status().isOk()));

        assertThat(result.get("outcome").asText()).isEqualTo("APPROVED");
        assertThat(result.get("newLimit").asInt()).isEqualTo(3000);
        assertThat(result.get("applied").asBoolean()).isTrue();
        assertThat(creditLimit(id)).isEqualTo(3000);
    }

    @Test
    void aLateMonthDeclinesTheIncreaseAsPastDue() throws Exception {
        JsonNode account = openAccount(openBody("APP-LATE", "Late Payer", "REWARDS_CARD",
                2000, 64000, 1350, 280));
        String id = account.get("accountId").asText();

        simulateMonth(UNDERWRITER, id, 20000, 0, true).andExpect(status().isOk());   // stmt 1
        simulateMonth(UNDERWRITER, id, 20000, 0, false).andExpect(status().isOk());  // stmt 1 unpaid → 30 DPD

        JsonNode result = read(postJsonAs(UNDERWRITER, "/api/v1/accounts/" + id + "/cli-requests",
                "{\"requestedLimit\":3000,\"acceptCounterOffer\":false}").andExpect(status().isOk()));

        assertThat(result.get("outcome").asText()).isEqualTo("DECLINED");
        assertThat(result.get("applied").asBoolean()).isFalse();
        assertThat(reasons(result)).contains("Account is past due");
        assertThat(creditLimit(id)).isEqualTo(2000);
    }

    private long newestMinimumDue(String publicId) {
        return jdbc.queryForObject(
                "SELECT minimum_due_cents FROM statement WHERE account_id = "
                        + "(SELECT id FROM account WHERE public_id = ?) ORDER BY period_end DESC, id DESC LIMIT 1",
                Long.class, publicId);
    }

    private int creditLimit(String publicId) {
        return jdbc.queryForObject("SELECT credit_limit FROM account WHERE public_id = ?", Integer.class, publicId);
    }

    private static java.util.List<String> reasons(JsonNode result) {
        java.util.List<String> reasons = new java.util.ArrayList<>();
        result.get("reasons").forEach(r -> reasons.add(r.asText()));
        return reasons;
    }
}
