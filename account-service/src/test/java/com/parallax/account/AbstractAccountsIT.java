package com.parallax.account;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.parallax.engine.config.RuleConfigs;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;

import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;

/**
 * Base for account-service integration tests. A shared Postgres 16 container (with the repo's
 * docker/postgres/init.sql, so accounts_owner/accounts_app and the accounts database exist) runs the
 * accounts schema; a WireMock stands in for application-service's {@code /internal/v1/rule-config/live}
 * (returning the LIVE v1.3 config, so CLI affordability is deterministic). Mutable tables and the public
 * id sequence are reset before each test.
 */
@SpringBootTest
@ActiveProfiles("dev")
@AutoConfigureMockMvc
public abstract class AbstractAccountsIT {

    protected static final String INTERNAL_TOKEN = "internal-dev";

    protected static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>(DockerImageName.parse("postgres:16"))
                    .withCopyFileToContainer(
                            MountableFile.forHostPath("../docker/postgres/init.sql"),
                            "/docker-entrypoint-initdb.d/init.sql");

    protected static final WireMockServer APP_INTERNAL = new WireMockServer(options().dynamicPort());

    static {
        POSTGRES.start();
        APP_INTERNAL.start();
    }

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        String url = "jdbc:postgresql://" + POSTGRES.getHost() + ":" + POSTGRES.getMappedPort(5432) + "/accounts";
        registry.add("spring.datasource.url", () -> url);
        registry.add("spring.datasource.username", () -> "accounts_app");
        registry.add("spring.datasource.password", () -> "app-dev");
        registry.add("spring.flyway.url", () -> url);
        registry.add("spring.flyway.user", () -> "accounts_owner");
        registry.add("spring.flyway.password", () -> "owner-dev");
        registry.add("spring.datasource.hikari.maximum-pool-size", () -> "4");
        registry.add("spring.datasource.hikari.minimum-idle", () -> "0");
        registry.add("parallax.application.url", () -> "http://localhost:" + APP_INTERNAL.port());
        registry.add("parallax.internal-token", () -> INTERNAL_TOKEN);
    }

    @Autowired
    protected MockMvc mvc;

    @Autowired
    protected JdbcTemplate jdbc;

    protected final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void resetDatabaseAndStubs() throws Exception {
        String url = "jdbc:postgresql://" + POSTGRES.getHost() + ":" + POSTGRES.getMappedPort(5432) + "/accounts";
        try (Connection c = DriverManager.getConnection(url, "accounts_owner", "owner-dev");
             Statement s = c.createStatement()) {
            s.execute("TRUNCATE cli_request, collection_action, card_transaction, statement, account "
                    + "RESTART IDENTITY CASCADE");
            s.execute("ALTER SEQUENCE account_public_seq RESTART WITH 88201");
        }
        APP_INTERNAL.resetAll();
        String config = objectMapper.writeValueAsString(RuleConfigs.v1_3());
        APP_INTERNAL.stubFor(get(urlEqualTo("/internal/v1/rule-config/live"))
                .willReturn(okJson("{\"version\":\"v1.3\",\"config\":" + config + "}")));
    }

    // --- request helpers -----------------------------------------------------------------------------

    protected JsonNode openAccount(String body) throws Exception {
        String response = mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .post("/internal/v1/accounts")
                        .header("X-Internal-Token", INTERNAL_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response);
    }

    protected ResultActions simulateMonth(String user, String accountId, long purchases, long payment,
                                          boolean payOnTime) throws Exception {
        String body = "{\"purchasesCents\":" + purchases + ",\"paymentCents\":" + payment
                + ",\"payOnTime\":" + payOnTime + "}";
        return mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .post("/api/v1/accounts/" + accountId + "/simulate-month")
                .with(httpBasic(user, "demo-password"))
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    protected ResultActions getAs(String user, String path) throws Exception {
        return mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .get(path).with(httpBasic(user, "demo-password")));
    }

    protected ResultActions postJsonAs(String user, String path, String body) throws Exception {
        return mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .post(path).with(httpBasic(user, "demo-password"))
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    protected JsonNode read(ResultActions actions) throws Exception {
        return objectMapper.readTree(actions.andReturn().getResponse().getContentAsString());
    }

    protected String openBody(String applicationId, String displayName, String product, int creditLimit,
                              int income, int housing, int debt) {
        return "{\"applicationId\":\"" + applicationId + "\",\"displayName\":\"" + displayName
                + "\",\"product\":\"" + product + "\",\"creditLimit\":" + creditLimit
                + ",\"annualIncome\":" + income + ",\"monthlyHousing\":" + housing
                + ",\"monthlyDebt\":" + debt + ",\"ruleVersion\":\"v1.3\",\"ledgerSeq\":1}";
    }
}
