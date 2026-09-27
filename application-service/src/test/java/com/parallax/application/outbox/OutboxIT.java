package com.parallax.application.outbox;

import com.fasterxml.jackson.databind.JsonNode;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.parallax.application.decision.CommitFaultHook;
import com.parallax.application.intake.AbstractIntakeIT;
import com.parallax.application.jobs.OutboxPublisher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.LinkedHashMap;
import java.util.Map;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The transactional outbox (SPEC §13): an APPROVED decision (or an override to APPROVED) enqueues exactly
 * one ACCOUNT_OPEN_REQUESTED event in the decision transaction, the publisher delivers it once (retrying
 * on failure), and SEED rows never enqueue anything. account-service is stood in for by WireMock.
 */
class OutboxIT extends AbstractIntakeIT {

    private static final String UNDERWRITER = "priya.menon@parallax.dev";
    private static final String ACCOUNTS_PATH = "/internal/v1/accounts";

    protected static final WireMockServer ACCOUNTS = new WireMockServer(options().dynamicPort());

    static {
        ACCOUNTS.start();
    }

    @DynamicPropertySource
    static void accountsProperties(DynamicPropertyRegistry registry) {
        registry.add("parallax.accounts.url", () -> "http://localhost:" + ACCOUNTS.port());
    }

    @Autowired
    OutboxPublisher publisher;

    /** A no-op fault hook by default; the atomicity test stubs it to throw after the ledger insert. */
    @MockitoBean
    CommitFaultHook faultHook;

    @BeforeEach
    void resetAccounts() {
        ACCOUNTS.resetAll();
    }

    @Test
    void approvedApplicationEnqueuesExactlyOneEvent() throws Exception {
        stubBureau("912345678", primeResponse("BP-OUT-1", "48 Elm Street, Columbus OH"));

        JsonNode body = decide("client@parallax.dev", defaultRequest());
        assertThat(body.get("outcome").asText()).isEqualTo("APPROVED");

        assertThat(count("outbox")).isEqualTo(1);
        Map<String, Object> row = jdbc.queryForMap(
                "SELECT event_type, aggregate_id, published_at, attempts FROM outbox");
        assertThat(row.get("event_type")).isEqualTo("ACCOUNT_OPEN_REQUESTED");
        assertThat(row.get("aggregate_id")).isEqualTo(body.get("applicationId").asText());
        assertThat(row.get("published_at")).isNull();
        assertThat(row.get("attempts")).isEqualTo(0);
    }

    @Test
    void faultAfterLedgerInsertRollsBackLedgerAndOutbox() throws Exception {
        Mockito.doThrow(new IllegalStateException("boom")).when(faultHook).afterLedgerInsert();
        stubBureau("912345678", primeResponse("BP-OUT-2", "48 Elm Street, Columbus OH"));

        submit("client@parallax.dev", newKey(), defaultRequest()).andExpect(status().is5xxServerError());

        assertThat(count("decision_ledger")).isZero();
        assertThat(count("outbox")).isZero();
    }

    @Test
    void publisherDeliversOnceAndThenHasNothingLeft() throws Exception {
        ACCOUNTS.stubFor(post(urlEqualTo(ACCOUNTS_PATH)).willReturn(aResponse().withStatus(201)));
        stubBureau("912345678", primeResponse("BP-OUT-3", "48 Elm Street, Columbus OH"));
        decide("client@parallax.dev", defaultRequest());

        assertThat(publisher.runOnce()).hasSize(1);
        ACCOUNTS.verify(1, postRequestedFor(urlEqualTo(ACCOUNTS_PATH)));
        assertThat(unpublishedCount()).isZero();

        // A second run has nothing to publish.
        assertThat(publisher.runOnce()).isEmpty();
        ACCOUNTS.verify(1, postRequestedFor(urlEqualTo(ACCOUNTS_PATH)));
    }

    @Test
    void aServerErrorIncrementsAttempts() throws Exception {
        ACCOUNTS.stubFor(post(urlEqualTo(ACCOUNTS_PATH)).willReturn(aResponse().withStatus(500)));
        stubBureau("912345678", primeResponse("BP-OUT-4", "48 Elm Street, Columbus OH"));
        decide("client@parallax.dev", defaultRequest());

        assertThat(publisher.runOnce()).isEmpty();

        Map<String, Object> row = jdbc.queryForMap(
                "SELECT attempts, published_at, last_error FROM outbox");
        assertThat(row.get("attempts")).isEqualTo(1);
        assertThat(row.get("published_at")).isNull();
        assertThat(row.get("last_error")).isEqualTo("HTTP 500");
    }

    @Test
    void declinedDecisionEnqueuesNothing() throws Exception {
        stubBureau("961000001", referenceResponse("BP-OUT-5", "961000001", "12 Vine St, Columbus OH", 1995));

        JsonNode body = decide("client@parallax.dev", subprimeRequest());
        assertThat(body.get("outcome").asText()).isEqualTo("DECLINED");
        assertThat(count("outbox")).isZero();
    }

    @Test
    void overrideToApprovedEnqueuesOneEvent() throws Exception {
        stubBureau("931000003", referenceResponse("BP-OUT-6", "931000003", "34 Oak St, Columbus OH", 1996));
        JsonNode body = decide("client@parallax.dev", nearPrimeRequest());
        assertThat(body.get("outcome").asText()).isEqualTo("REFER");
        assertThat(count("outbox")).isZero();

        String override = json(Map.of("decision", "APPROVED", "creditLimit", 2000,
                "overrideCode", "O2", "note", "Income verified with recent paystubs"));
        postJsonAs(UNDERWRITER, "/api/v1/reviews/" + body.get("applicationId").asText(), override)
                .andExpect(status().isCreated());

        assertThat(count("outbox")).isEqualTo(1);
    }

    @Test
    void seedLedgerRowsEnqueueNothing() {
        for (int i = 1; i <= 100; i++) {
            jdbc.update("INSERT INTO decision_ledger (seq, kind, source, outcome, created_at, prev_hash, hash) "
                            + "VALUES (?, 'DECISION', 'SEED', 'APPROVED', now(), ?, ?)",
                    i, "0".repeat(64), String.format("%064d", i));
        }
        assertThat(count("outbox")).isZero();
    }

    private int unpublishedCount() {
        Integer n = jdbc.queryForObject("SELECT count(*) FROM outbox WHERE published_at IS NULL", Integer.class);
        return n == null ? 0 : n;
    }

    private Map<String, Object> subprimeRequest() {
        Map<String, Object> body = new LinkedHashMap<>(defaultRequest());
        body.put("firstName", "Priya");
        body.put("lastName", "Sharma");
        body.put("ssn", "961000001");
        body.put("dateOfBirth", "1995-01-15");
        body.put("address", "12 Vine St, Columbus OH");
        body.put("annualIncome", 38000);
        body.put("monthlyHousing", 1300);
        body.put("monthlyDebt", 520);
        body.put("product", "REWARDS_CARD");
        return body;
    }

    private Map<String, Object> nearPrimeRequest() {
        Map<String, Object> body = new LinkedHashMap<>(defaultRequest());
        body.put("firstName", "Sara");
        body.put("lastName", "Khan");
        body.put("ssn", "931000003");
        body.put("dateOfBirth", "1996-03-10");
        body.put("address", "34 Oak St, Columbus OH");
        body.put("annualIncome", 45000);
        body.put("monthlyHousing", 1200);
        body.put("monthlyDebt", 300);
        body.put("product", "REWARDS_CARD");
        return body;
    }
}
