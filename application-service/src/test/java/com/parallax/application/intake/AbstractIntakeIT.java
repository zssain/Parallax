package com.parallax.application.intake;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.parallax.application.AbstractPostgresIT;
import com.parallax.application.decision.DecisionClient;
import com.parallax.bureau.contract.CreditReportResponse;
import com.parallax.bureau.contract.PullTypeEnum;
import com.parallax.engine.api.EvaluateRequest;
import com.parallax.engine.api.EvaluateResponse;
import com.parallax.engine.model.Decision;
import com.parallax.engine.model.EngineVersion;
import com.parallax.engine.model.ScorecardVersion;
import com.parallax.engine.scoring.DecisionEngine;
import org.mockito.Mockito;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.Marshaller;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.io.StringWriter;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.containing;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * Base for intake integration tests. Runs a WireMock SOAP bureau (responses rendered from the JAXB
 * classes), points {@code parallax.bureau.url} at it, and truncates the mutable tables (as the schema
 * owner, since parallax_app cannot delete them) before each test.
 */
@AutoConfigureMockMvc
public abstract class AbstractIntakeIT extends AbstractPostgresIT {

    protected static final WireMockServer BUREAU = new WireMockServer(options().dynamicPort());

    static {
        BUREAU.start();
    }

    protected final ObjectMapper objectMapper = new ObjectMapper();

    @Autowired
    protected MockMvc mvc;

    @Autowired
    protected JdbcTemplate jdbc;

    /** The decision engine is called in-process in tests, so ITs need no running decision-service. */
    @MockitoBean
    protected DecisionClient decisionClient;

    @org.springframework.beans.factory.annotation.Autowired
    protected com.parallax.application.bureau.BureauCircuitControl bureauCircuit;

    @DynamicPropertySource
    static void bureauProperties(DynamicPropertyRegistry registry) {
        registry.add("parallax.bureau.url", () -> "http://localhost:" + BUREAU.port() + "/ws");
    }

    @BeforeEach
    void resetBureauAndDatabase() throws Exception {
        BUREAU.resetAll();
        bureauCircuit.close(); // reset the shared circuit breaker between tests
        stubDecisionEngineInProcess();
        String url = "jdbc:postgresql://" + POSTGRES.getHost() + ":" + POSTGRES.getMappedPort(5432) + "/parallax";
        try (Connection c = DriverManager.getConnection(url, "parallax_owner", "owner-dev");
             Statement s = c.createStatement()) {
            s.execute("TRUNCATE decision_ledger, application, bureau_pull, idempotency_key RESTART IDENTITY CASCADE");
        }
    }

    /** Make the mock DecisionClient run the real engine in-process (the default; also used to "heal"). */
    protected void stubDecisionEngineInProcess() {
        // doAnswer (not when/thenAnswer) so re-stubbing does not re-invoke the current answer.
        Mockito.doAnswer(invocation -> {
            EvaluateRequest request = invocation.getArgument(0);
            Decision decision = DecisionEngine.evaluate(request.input(), request.config());
            return new EvaluateResponse(request.ruleVersion(), EngineVersion.VALUE, ScorecardVersion.VALUE, decision);
        }).when(decisionClient).evaluate(Mockito.any());
    }

    /** Make the mock DecisionClient fail as if the engine is down. */
    protected void stubDecisionEngineDown() {
        Mockito.doThrow(new com.parallax.application.decision.DecisionUnavailableException("engine down", null))
                .when(decisionClient).evaluate(Mockito.any());
    }

    // --- request helpers -------------------------------------------------------------------------

    protected String newKey() {
        return UUID.randomUUID().toString();
    }

    protected Map<String, Object> defaultRequest() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("firstName", "Ishaan");
        body.put("lastName", "Kapoor");
        body.put("dateOfBirth", "1996-04-18");
        body.put("ssn", "912345678");
        body.put("address", "48 Elm Street, Columbus OH");
        body.put("annualIncome", 64000);
        body.put("monthlyHousing", 1350);
        body.put("monthlyDebt", 280);
        body.put("independentIncome", true);
        body.put("bureauConsent", true);
        body.put("product", "REWARDS_CARD");
        return body;
    }

    protected String json(Map<String, Object> body) {
        try {
            return objectMapper.writeValueAsString(body);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    // --- response / db inspection helpers --------------------------------------------------------

    protected JsonNode read(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    protected int count(String table) {
        Integer n = jdbc.queryForObject("SELECT count(*) FROM " + table, Integer.class);
        return n == null ? 0 : n;
    }

    protected List<String> stepNames(JsonNode responseBody) {
        List<String> names = new ArrayList<>();
        responseBody.get("pipeline").forEach(item -> names.add(item.get("step").asText()));
        return names;
    }

    protected JsonNode step(JsonNode responseBody, String stepName) {
        for (JsonNode item : responseBody.get("pipeline")) {
            if (item.get("step").asText().equals(stepName)) {
                return item;
            }
        }
        throw new AssertionError("no pipeline step " + stepName);
    }

    protected ResultActions submit(String username, String idempotencyKey, Map<String, Object> body)
            throws Exception {
        MockHttpServletRequestBuilder request = post("/api/v1/applications")
                .with(httpBasic(username, "demo-password"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(body));
        if (idempotencyKey != null) {
            request = request.header("Idempotency-Key", idempotencyKey);
        }
        return mvc.perform(request);
    }

    protected ResultActions getAs(String username, String path) throws Exception {
        return mvc.perform(get(path).with(httpBasic(username, "demo-password")));
    }

    protected ResultActions postJsonAs(String username, String path, String bodyJson) throws Exception {
        return mvc.perform(post(path).with(httpBasic(username, "demo-password"))
                .contentType(MediaType.APPLICATION_JSON).content(bodyJson));
    }

    protected ResultActions postAs(String username, String path) throws Exception {
        return mvc.perform(post(path).with(httpBasic(username, "demo-password")));
    }

    /** Submit and return the decision response body as JSON. */
    protected JsonNode decide(String username, Map<String, Object> body) throws Exception {
        return read(submit(username, newKey(), body).andReturn());
    }

    // --- bureau stub helpers ---------------------------------------------------------------------

    protected void stubBureau(String ssn, CreditReportResponse response) {
        BUREAU.stubFor(WireMock.post(urlEqualTo("/ws")).withRequestBody(containing(ssn))
                .willReturn(aResponse().withStatus(200).withHeader("Content-Type", "text/xml")
                        .withBody(soapEnvelope(response))));
    }

    protected void stubBureauDelayed(String ssn, CreditReportResponse response, int delayMs) {
        BUREAU.stubFor(WireMock.post(urlEqualTo("/ws")).withRequestBody(containing(ssn))
                .willReturn(aResponse().withStatus(200).withHeader("Content-Type", "text/xml")
                        .withFixedDelay(delayMs).withBody(soapEnvelope(response))));
    }

    protected void stubBureauServerError() {
        BUREAU.stubFor(WireMock.post(urlEqualTo("/ws")).willReturn(aResponse().withStatus(500)));
    }

    protected CreditReportResponse primeResponse(String pullId, String fileAddress) {
        return response(pullId, "PRIME", fileAddress, 12, 0, 0, "0.080", 156, 1997, false);
    }

    /** A bureau response derived from a synthetic SSN per SPEC §8 (profile + scenario). */
    protected CreditReportResponse referenceResponse(String pullId, String ssn, String requestAddress,
                                                     int birthYear) {
        char profileDigit = ssn.charAt(1);
        char scenarioDigit = ssn.charAt(2);
        String profile = switch (profileDigit) {
            case '3', '4', '5' -> "NEAR_PRIME";
            case '6', '7' -> "SUBPRIME";
            case '8' -> "THIN_FILE";
            default -> "PRIME";
        };
        boolean mismatch = scenarioDigit == '7';
        boolean beforeDob = scenarioDigit == '8';
        boolean deceased = scenarioDigit == '9';
        String fileAddress = mismatch ? "14 Old Mill Rd, Dayton OH" : requestAddress;
        int ssnIssuanceYear = beforeDob ? birthYear - 3 : birthYear + 1;
        return switch (profile) {
            case "NEAR_PRIME" -> response(pullId, profile, fileAddress, 5, 3, 0, "0.550", 40, ssnIssuanceYear, deceased);
            case "SUBPRIME" -> response(pullId, profile, fileAddress, 4, 5, 2, "0.820", 30, ssnIssuanceYear, deceased);
            case "THIN_FILE" -> response(pullId, profile, fileAddress, 1, 1, 0, "0.200", 10, ssnIssuanceYear, deceased);
            default -> response(pullId, profile, fileAddress, 12, 0, 0, "0.080", 156, ssnIssuanceYear, deceased);
        };
    }

    protected CreditReportResponse response(String pullId, String profile, String fileAddress,
                                            int tradelines, int inquiries, int delinquencies,
                                            String utilization, int fileAgeMonths,
                                            int ssnIssuanceYear, boolean deceased) {
        CreditReportResponse response = new CreditReportResponse();
        response.setPullId(pullId);
        response.setPullType(PullTypeEnum.HARD);
        response.setFileAddress(fileAddress);
        response.setSsnIssuanceYear(ssnIssuanceYear);
        response.setDeceasedIndicator(deceased);
        response.setOpenTradelines(tradelines);
        response.setInquiries6M(inquiries);
        response.setDelinquencies24M(delinquencies);
        response.setRevolvingUtilization(new BigDecimal(utilization));
        response.setFileAgeMonths(fileAgeMonths);
        response.setProfileLabel(profile);
        return response;
    }

    protected static String soapEnvelope(CreditReportResponse response) {
        return "<soapenv:Envelope xmlns:soapenv=\"http://schemas.xmlsoap.org/soap/envelope/\">"
                + "<soapenv:Header/><soapenv:Body>" + marshalFragment(response)
                + "</soapenv:Body></soapenv:Envelope>";
    }

    private static String marshalFragment(CreditReportResponse response) {
        try {
            Marshaller marshaller = JAXBContext.newInstance(CreditReportResponse.class).createMarshaller();
            marshaller.setProperty(Marshaller.JAXB_FRAGMENT, true);
            StringWriter writer = new StringWriter();
            marshaller.marshal(response, writer);
            return writer.toString();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to marshal stub response", e);
        }
    }
}
