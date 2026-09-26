package com.parallax.application.system;

import com.parallax.application.bureau.BureauCircuitControl;
import com.parallax.application.jobs.RedecisionJob;
import com.parallax.application.pii.DataCipher;
import com.parallax.application.query.DisplayNamePolicy;
import com.parallax.application.rules.LiveRuleService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Backs the System screen (SPEC §15): service health, the bureau circuit breaker, the re-decision
 * queue, recent idempotency keys and bureau pulls, plus the dev-only bureau fault switch. Each health
 * probe has a 1 s timeout and reports UP/DOWN with measured latency.
 */
@Service
public class SystemService {

    private static final Duration PROBE_TIMEOUT = Duration.ofSeconds(1);

    /** Same criteria as {@link RedecisionJob}: BUREAU_UNAVAILABLE apps whose latest row is the B01 REFER. */
    private static final String REDECISION_QUEUE_SQL = """
            SELECT a.public_id, a.name_enc, a.name_masked
            FROM application a
            JOIN LATERAL (SELECT kind, outcome, reason_codes FROM decision_ledger d
                          WHERE d.application_id = a.id ORDER BY d.seq DESC LIMIT 1) dl ON true
            WHERE a.status = 'BUREAU_UNAVAILABLE'
              AND dl.kind = 'DECISION' AND dl.outcome = 'REFER' AND dl.reason_codes @> '["B01"]'::jsonb
            ORDER BY a.created_at ASC
            """;

    private static final String IDEMPOTENCY_KEYS_SQL = """
            SELECT idem_key, client_id, state, application_public_id, created_at, expires_at
            FROM idempotency_key ORDER BY created_at DESC LIMIT 8
            """;

    private static final String BUREAU_PULLS_SQL = """
            SELECT bp.id, bp.pull_type, bp.profile, bp.pulled_at,
                   (SELECT a.ssn_last4 FROM application a WHERE a.ssn_token = bp.ssn_token
                    ORDER BY a.created_at DESC LIMIT 1) AS ssn_last4
            FROM bureau_pull bp ORDER BY bp.pulled_at DESC LIMIT 8
            """;

    private final JdbcTemplate jdbc;
    private final BureauCircuitControl circuit;
    private final RedecisionJob redecisionJob;
    private final LiveRuleService liveRuleService;
    private final DataCipher dataCipher;
    private final DisplayNamePolicy displayNamePolicy;
    private final RestClient restClient;
    private final String decisionUrl;
    private final String bureauBaseUrl;
    private final String assistantUrl;

    public SystemService(JdbcTemplate jdbc, BureauCircuitControl circuit, RedecisionJob redecisionJob,
                         LiveRuleService liveRuleService, DataCipher dataCipher,
                         DisplayNamePolicy displayNamePolicy, RestClient.Builder builder,
                         @Value("${parallax.decision.url:http://localhost:8081}") String decisionUrl,
                         @Value("${parallax.bureau.url:http://localhost:8082/ws}") String bureauUrl,
                         @Value("${parallax.assistant.url:http://localhost:8083}") String assistantUrl) {
        this.jdbc = jdbc;
        this.circuit = circuit;
        this.redecisionJob = redecisionJob;
        this.liveRuleService = liveRuleService;
        this.dataCipher = dataCipher;
        this.displayNamePolicy = displayNamePolicy;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(PROBE_TIMEOUT);
        factory.setReadTimeout(PROBE_TIMEOUT);
        this.restClient = builder.requestFactory(factory).build();
        this.decisionUrl = decisionUrl;
        this.bureauBaseUrl = bureauUrl.replaceAll("/ws/?$", "");
        this.assistantUrl = assistantUrl;
    }

    // --- status -----------------------------------------------------------------------------------

    public SystemViews.Status status(String role) {
        List<SystemViews.RedecisionItem> queue = jdbc.query(REDECISION_QUEUE_SQL, (rs, i) ->
                new SystemViews.RedecisionItem(rs.getString("public_id"),
                        displayName(role, rs.getBytes("name_enc"), rs.getString("name_masked"))));
        long enginePending = count("ENGINE_PENDING");
        long engineFailedManual = count("ENGINE_FAILED_MANUAL");
        return new SystemViews.Status(circuit.state(), queue, enginePending, engineFailedManual,
                liveRuleService.current().version(), services());
    }

    /** The five services in the fixed SPEC §15 order, each with measured latency (1 s timeout). */
    private List<SystemViews.Service> services() {
        List<SystemViews.Service> services = new ArrayList<>();
        long selfStart = System.nanoTime();
        services.add(new SystemViews.Service("application-service", "UP", elapsedMs(selfStart)));
        services.add(httpHealth("decision-service", decisionUrl + "/actuator/health"));
        services.add(httpHealth("bureau-mock (SOAP)", bureauBaseUrl + "/actuator/health"));
        services.add(httpHealth("assistant-service", assistantUrl + "/actuator/health"));
        services.add(postgresHealth());
        return services;
    }

    private SystemViews.Service httpHealth(String name, String url) {
        long start = System.nanoTime();
        try {
            restClient.get().uri(url).retrieve().toBodilessEntity();
            return new SystemViews.Service(name, "UP", elapsedMs(start));
        } catch (RuntimeException e) {
            return new SystemViews.Service(name, "DOWN", elapsedMs(start));
        }
    }

    private SystemViews.Service postgresHealth() {
        long start = System.nanoTime();
        try {
            jdbc.queryForObject("SELECT 1", Integer.class);
            return new SystemViews.Service("postgres", "UP", elapsedMs(start));
        } catch (RuntimeException e) {
            return new SystemViews.Service("postgres", "DOWN", elapsedMs(start));
        }
    }

    // --- bureau fault (dev) -----------------------------------------------------------------------

    /** Push a fault to bureau-mock and align the circuit (SPEC §15). Returns the resulting state. */
    public SystemViews.BureauFaultResult bureauFault(BureauFaultRequest request) {
        String mode = request.mode() == null ? "NONE" : request.mode();
        int delayMs = request.delayMs() == null ? 0 : request.delayMs();
        restClient.post().uri(bureauBaseUrl + "/admin/fault")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("mode", mode, "delayMs", delayMs))
                .retrieve().toBodilessEntity();

        List<String> redecided = List.of();
        switch (mode) {
            case "DOWN" -> circuit.forceOpen();     // next application falls back at once
            case "NONE" -> {
                circuit.close();
                redecided = redecisionJob.runOnce(); // recover stranded applications now
            }
            default -> { /* SLOW: mock setting only */ }
        }
        return new SystemViews.BureauFaultResult(mode, request.delayMs(), circuit.state(), redecided);
    }

    // --- recent activity --------------------------------------------------------------------------

    public List<SystemViews.IdempotencyKeyView> idempotencyKeys() {
        return jdbc.query(IDEMPOTENCY_KEYS_SQL, (rs, i) -> new SystemViews.IdempotencyKeyView(
                rs.getString("idem_key"),
                rs.getString("client_id"),
                rs.getString("state"),
                rs.getString("application_public_id"),
                rs.getObject("created_at", OffsetDateTime.class).toInstant(),
                rs.getObject("expires_at", OffsetDateTime.class).toInstant()));
    }

    public List<SystemViews.BureauPullView> bureauPulls() {
        return jdbc.query(BUREAU_PULLS_SQL, (rs, i) -> new SystemViews.BureauPullView(
                rs.getString("id"),
                rs.getString("pull_type"),
                rs.getString("profile"),
                trim(rs.getString("ssn_last4")),
                rs.getObject("pulled_at", OffsetDateTime.class).toInstant()));
    }

    // --- helpers ----------------------------------------------------------------------------------

    private long count(String status) {
        Long n = jdbc.queryForObject("SELECT count(*) FROM application WHERE status = ?", Long.class, status);
        return n == null ? 0 : n;
    }

    private String displayName(String role, byte[] nameEnc, String nameMasked) {
        return displayNamePolicy.fullName(role) ? dataCipher.decrypt(nameEnc) : nameMasked;
    }

    private static long elapsedMs(long startNanos) {
        return Math.max(0, (System.nanoTime() - startNanos) / 1_000_000);
    }

    private static String trim(String value) {
        return value == null ? null : value.trim();
    }
}
