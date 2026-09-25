package com.parallax.application.resilience;

import com.fasterxml.jackson.databind.JsonNode;
import com.parallax.application.intake.AbstractIntakeIT;
import com.parallax.application.jobs.RedecisionJob;
import com.parallax.application.ledger.LedgerEntry;
import com.parallax.application.ledger.LedgerKind;
import com.parallax.application.ledger.LedgerSource;
import com.parallax.application.ledger.LedgerWriter;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class RedecisionIT extends AbstractIntakeIT {

    private static final String USER = "priya.menon@parallax.dev";

    @Autowired
    RedecisionJob redecisionJob;
    @Autowired
    LedgerWriter ledgerWriter;
    @Autowired
    PlatformTransactionManager transactionManager;

    @Test
    void restoredBureauReDecidesPendingApplicationsButSkipsOverridden() throws Exception {
        // Two applications stranded at BUREAU_UNAVAILABLE (B01) while the bureau was down.
        stubBureauServerError();
        String pendingA = bureauUnavailableApp("912345678");
        String pendingB = bureauUnavailableApp("961234567");
        String overridden = bureauUnavailableApp("981234567");

        // Give the overridden application a later OVERRIDE row so the job skips it.
        long overriddenId = jdbc.queryForObject(
                "SELECT id FROM application WHERE public_id = ?", Long.class, overridden);
        long overriddenB01Seq = jdbc.queryForObject(
                "SELECT max(seq) FROM decision_ledger dl WHERE dl.application_id = ?", Long.class, overriddenId);
        new TransactionTemplate(transactionManager).executeWithoutResult(s ->
                ledgerWriter.append(LedgerEntry.builder(LedgerKind.OVERRIDE, LedgerSource.LIVE)
                        .application(overriddenId, overridden)
                        .linkedSeq(overriddenB01Seq)
                        .outcome("APPROVED").creditLimit(2000)
                        .overrideDetail(Map.of("by", USER, "code", "O5", "note", "manual override"))
                        .build()));

        // Bureau recovers.
        BUREAU.resetAll();
        stubBureau("912345678", referenceResponse("BP-RA", "912345678", "48 Elm Street, Columbus OH", 1996));
        stubBureau("961234567", referenceResponse("BP-RB", "961234567", "48 Elm Street, Columbus OH", 1996));
        bureauCircuit.close();

        List<String> processed = redecisionJob.runOnce();
        assertThat(processed).containsExactlyInAnyOrder(pendingA, pendingB);

        for (String id : List.of(pendingA, pendingB)) {
            JsonNode detail = read(getAs(USER, "/api/v1/applications/" + id).andReturn());
            assertThat(detail.get("status").asText()).isEqualTo("DECIDED");
            assertThat(trailKinds(detail)).containsExactly("DECISION", "REDECISION");
        }

        JsonNode overriddenDetail = read(getAs(USER, "/api/v1/applications/" + overridden).andReturn());
        assertThat(overriddenDetail.get("status").asText()).isEqualTo("BUREAU_UNAVAILABLE");

        JsonNode verify = read(getAs(USER, "/api/v1/ledger/verify").andReturn());
        assertThat(verify.get("ok").asBoolean()).isTrue();
    }

    private String bureauUnavailableApp(String ssn) throws Exception {
        Map<String, Object> body = defaultRequest();
        body.put("ssn", ssn);
        JsonNode response = read(submit(USER, newKey(), body).andExpect(status().isCreated()).andReturn());
        assertThat(response.get("status").asText()).isEqualTo("BUREAU_UNAVAILABLE");
        return response.get("applicationId").asText();
    }

    private List<String> trailKinds(JsonNode detail) {
        List<String> kinds = new ArrayList<>();
        detail.get("trail").forEach(item -> kinds.add(item.get("kind").asText()));
        return kinds;
    }
}
