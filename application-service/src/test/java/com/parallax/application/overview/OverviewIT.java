package com.parallax.application.overview;

import com.fasterxml.jackson.databind.JsonNode;
import com.parallax.application.drift.DriftService;
import com.parallax.application.lab.AbstractLabIT;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Overview on a crafted dataset (SPEC §15): one APPROVED, one REFER and one DECLINED LIVE decision,
 * a PROPOSED candidate running in shadow, and a drift report — verifying the counts, the ordered
 * attention list (exact strings) and a 12-month approval trend.
 */
class OverviewIT extends AbstractLabIT {

    @Autowired
    DriftService driftService;

    @Test
    void overviewCountsAttentionAndTrend() throws Exception {
        submitApp("912345678", 64000, 1350, 280, "1996-04-18");  // Ishaan → APPROVED 830
        submitApp("931000003", 45000, 1200, 300, "1998-03-10");  // near-prime → REFER 655
        submitApp("961000001", 38000, 1300, 520, "1995-07-22");  // subprime → DECLINED 445

        // A PROPOSED candidate, running in shadow.
        postJsonAs(STRATEGIST, "/api/v1/lab/versions", "{}").andExpect(status().isCreated());
        pollDone(startReplay(STRATEGIST, "v1.4"));
        postJsonAs(STRATEGIST, "/api/v1/lab/versions/v1.4/shadow", "{\"enabled\":true}")
                .andExpect(status().isOk());
        postAs(STRATEGIST, "/api/v1/lab/versions/v1.4/propose").andExpect(status().isOk());

        // A drift report so the drift attention line appears.
        driftService.clearBaselineCache();
        JsonNode drift = read(postAs(STRATEGIST, "/api/v1/drift/run").andReturn());

        JsonNode overview = read(getAs(STRATEGIST, "/api/v1/overview").andReturn());

        assertThat(overview.get("decisions").asLong()).isEqualTo(3);
        assertThat(overview.get("approved").asLong()).isEqualTo(1);
        assertThat(overview.get("refer").asLong()).isEqualTo(1);
        assertThat(overview.get("declined").asLong()).isEqualTo(1);
        assertThat(overview.get("approvalRate").asDouble()).isEqualTo(0.3333);
        assertThat(overview.get("reviewQueue").asLong()).isEqualTo(1);
        assertThat(overview.get("liveVersion").get("version").asText()).isEqualTo("v1.3");
        assertThat(overview.get("shadowVersion").asText()).isEqualTo("v1.4");

        // 12-month trend ending at the as-of month; this month is 1 of 3 approved.
        JsonNode trend = overview.get("approvalTrend");
        assertThat(trend).hasSize(12);
        String thisMonth = YearMonth.from(LocalDate.now(ZoneOffset.UTC)).toString();
        JsonNode last = trend.get(11);
        assertThat(last.get("month").asText()).isEqualTo(thisMonth);
        assertThat(last.get("rate").asDouble()).isEqualTo(0.3333);

        // Attention, in the SPEC §15 order, with exact strings.
        JsonNode attention = overview.get("attention");
        String psiText = "Score drift PSI " + String.format(Locale.US, "%.3f", drift.get("psi").asDouble())
                + " — " + drift.get("status").asText();
        assertThat(messagesOf(attention)).containsExactly(
                "1 application waiting in the review queue",
                "v1.4 proposed by Aditi Rao — needs a second approver",
                psiText,
                "v1.4 is running in shadow mode on live traffic");

        assertThat(attention.get(0).get("target").asText()).isEqualTo("queue");
        assertThat(attention.get(0).get("severity").asText()).isEqualTo("warn");
        assertThat(attention.get(1).get("target").asText()).isEqualTo("lab:v1.4");
        assertThat(attention.get(1).get("severity").asText()).isEqualTo("info");
        assertThat(attention.get(2).get("target").asText()).isEqualTo("drift");
        assertThat(attention.get(3).get("severity").asText()).isEqualTo("acc");
    }

    private void submitApp(String ssn, int income, int housing, int debt, String dob) throws Exception {
        int birthYear = Integer.parseInt(dob.substring(0, 4));
        String address = "100 Overview St, Columbus OH";
        stubBureau(ssn, referenceResponse("BP-" + ssn, ssn, address, birthYear));
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("firstName", "Over");
        body.put("lastName", "View");
        body.put("dateOfBirth", dob);
        body.put("ssn", ssn);
        body.put("address", address);
        body.put("annualIncome", income);
        body.put("monthlyHousing", housing);
        body.put("monthlyDebt", debt);
        body.put("independentIncome", true);
        body.put("bureauConsent", true);
        body.put("product", "REWARDS_CARD");
        submit(STRATEGIST, newKey(), body).andExpect(status().isCreated());
    }

    private java.util.List<String> messagesOf(JsonNode attention) {
        java.util.List<String> messages = new java.util.ArrayList<>();
        attention.forEach(item -> messages.add(item.get("message").asText()));
        return messages;
    }
}
