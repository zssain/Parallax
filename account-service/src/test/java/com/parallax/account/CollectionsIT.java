package com.parallax.account;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Delinquency buckets, the work queue and actions (SPEC §13, §15). */
class CollectionsIT extends AbstractAccountsIT {

    private static final String UNDERWRITER = "priya.menon@parallax.dev";

    @Test
    void twoMissedMonthsLandInThe60To89Bucket() throws Exception {
        JsonNode account = openAccount(openBody("APP-DEBTOR", "Debtor One", "REWARDS_CARD",
                2000, 64000, 1350, 280));
        String id = account.get("accountId").asText();

        simulateMonth(UNDERWRITER, id, 20000, 0, true).andExpect(status().isOk());  // stmt 1 (no prior → 0 DPD)
        simulateMonth(UNDERWRITER, id, 20000, 0, true).andExpect(status().isOk());  // stmt 1 unpaid → 30 DPD
        simulateMonth(UNDERWRITER, id, 20000, 0, true).andExpect(status().isOk());  // stmt 2 unpaid → 60 DPD

        assertThat(daysPastDue(id)).isEqualTo(60);
        assertThat(accountStatus(id)).isEqualTo("DELINQUENT");

        JsonNode queue = read(getAs(UNDERWRITER, "/api/v1/collections").andExpect(status().isOk()));
        JsonNode item = itemFor(queue, id);
        assertThat(item.get("bucket").asText()).isEqualTo("60-89");
        assertThat(item.get("priority").asText()).isEqualTo("HIGH");
        assertThat(item.get("daysPastDue").asInt()).isEqualTo(60);
        assertThat(item.get("lastContactAt").isNull()).isTrue();

        // The bucketed query returns it too; other buckets do not.
        JsonNode bucketed = read(getAs(UNDERWRITER, "/api/v1/collections?bucket=60-89"));
        assertThat(bucketed).hasSize(1);
        assertThat(read(getAs(UNDERWRITER, "/api/v1/collections?bucket=1-29"))).isEmpty();

        // Summary counts match: exactly one account, in the 60-89 bucket.
        JsonNode summary = read(getAs(UNDERWRITER, "/api/v1/collections/summary").andExpect(status().isOk()));
        assertThat(summary.get("buckets")).hasSize(4);
        assertThat(bucketCount(summary, "60-89")).isEqualTo(1);
        assertThat(bucketCount(summary, "30-59")).isZero();

        // An action sets lastContactAt.
        postJsonAs(UNDERWRITER, "/api/v1/collections/" + id + "/actions",
                "{\"type\":\"CONTACTED\",\"note\":\"Left a voicemail about the past-due balance\"}")
                .andExpect(status().isCreated());
        JsonNode afterAction = itemFor(read(getAs(UNDERWRITER, "/api/v1/collections")), id);
        assertThat(afterAction.get("lastContactAt").isNull()).isFalse();
    }

    private int daysPastDue(String publicId) {
        return jdbc.queryForObject("SELECT days_past_due FROM account WHERE public_id = ?", Integer.class, publicId);
    }

    private String accountStatus(String publicId) {
        return jdbc.queryForObject("SELECT status FROM account WHERE public_id = ?", String.class, publicId);
    }

    private static JsonNode itemFor(JsonNode queue, String accountId) {
        for (JsonNode item : queue) {
            if (item.get("accountId").asText().equals(accountId)) {
                return item;
            }
        }
        throw new AssertionError("account " + accountId + " not in the collections queue");
    }

    private static long bucketCount(JsonNode summary, String bucket) {
        for (JsonNode b : summary.get("buckets")) {
            if (b.get("bucket").asText().equals(bucket)) {
                return b.get("count").asLong();
            }
        }
        throw new AssertionError("no bucket " + bucket);
    }
}
