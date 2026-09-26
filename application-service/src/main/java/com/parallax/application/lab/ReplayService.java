package com.parallax.application.lab;

import com.parallax.application.error.ApiProblem;
import com.parallax.application.error.ConfigValidationException;
import com.parallax.application.json.CanonicalJson;
import com.parallax.application.rules.LiveRule;
import com.parallax.application.rules.LiveRuleService;
import com.parallax.engine.config.RuleConfigValidator;
import com.parallax.engine.model.RuleConfig;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Starts Strategy Lab replays and resolves existing ones (SPEC §10, §15). A STRATEGIST queues a new
 * job (after validating the version's status and config, and rejecting a concurrent run); an ASSISTANT
 * never starts work — it returns the newest DONE job for the version's current config, or 404.
 */
@Service
public class ReplayService {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final JdbcTemplate jdbc;
    private final CanonicalJson canonicalJson;
    private final LiveRuleService liveRuleService;
    private final ReplayJobRunner runner;
    private final ThreadPoolTaskExecutor replayExecutor;
    private final Clock clock;

    public ReplayService(JdbcTemplate jdbc, CanonicalJson canonicalJson, LiveRuleService liveRuleService,
                         ReplayJobRunner runner, ThreadPoolTaskExecutor replayExecutor, Clock clock) {
        this.jdbc = jdbc;
        this.canonicalJson = canonicalJson;
        this.liveRuleService = liveRuleService;
        this.runner = runner;
        this.replayExecutor = replayExecutor;
        this.clock = clock;
    }

    /** STRATEGIST: validate, queue a replay_job and submit it; returns the new job id (SPEC §10). */
    public String startReplay(String version, ReplayRequest request, String createdBy) {
        Map<String, Object> row = versionRow(version);
        RuleConfig config = canonicalJson.read((String) row.get("config"), RuleConfig.class);
        String configHash = ((String) row.get("config_hash")).trim();
        String status = (String) row.get("status");

        if (!"DRAFT".equals(status) && !"REPLAYED".equals(status)) {
            throw new ApiProblem(HttpStatus.UNPROCESSABLE_ENTITY, "Unprocessable Entity",
                    "Version " + version + " must be DRAFT or REPLAYED to replay (is " + status + ")");
        }
        List<String> errors = RuleConfigValidator.validate(config);
        if (!errors.isEmpty()) {
            throw new ConfigValidationException("Invalid rule configuration for " + version, errors);
        }
        Integer active = jdbc.queryForObject(
                "SELECT count(*) FROM replay_job WHERE status IN ('QUEUED','RUNNING')", Integer.class);
        if (active != null && active > 0) {
            throw new ApiProblem(HttpStatus.CONFLICT, "Conflict", "A replay is already running");
        }

        LiveRule live = liveRuleService.current();
        String jobId = "RJ-" + String.format("%06X", RANDOM.nextInt(0x1000000));
        jdbc.update("INSERT INTO replay_job (id, candidate_version, baseline_version, candidate_config_hash,"
                        + " status, progress, created_by, created_at) VALUES (?, ?, ?, ?, 'QUEUED', 0, ?, ?)",
                jobId, version, live.version(), configHash, createdBy, Timestamp.from(Instant.now(clock)));

        ReplayJobRunner.Job job = new ReplayJobRunner.Job(jobId, version, config, configHash,
                live.version(), live.config(), request == null ? null : request.from(),
                request == null ? null : request.to());
        replayExecutor.execute(() -> runner.run(job));
        return jobId;
    }

    /** ASSISTANT: the newest DONE job whose config matches the version's current config, else 404. */
    public String existingReplayJob(String version) {
        String configHash;
        try {
            configHash = jdbc.queryForObject(
                    "SELECT config_hash FROM rule_version WHERE version = ?", String.class, version).trim();
        } catch (EmptyResultDataAccessException e) {
            throw new ApiProblem(HttpStatus.NOT_FOUND, "Not Found", "No rule version " + version);
        }
        String jobId = jdbc.query("SELECT id FROM replay_job WHERE status = 'DONE' AND candidate_config_hash = ?"
                        + " ORDER BY finished_at DESC NULLS LAST LIMIT 1",
                rs -> rs.next() ? rs.getString(1) : null, configHash);
        if (jobId == null) {
            throw new ApiProblem(HttpStatus.NOT_FOUND, "Not Found", "No replay exists for this version");
        }
        return jobId;
    }

    private Map<String, Object> versionRow(String version) {
        try {
            return jdbc.queryForMap(
                    "SELECT status, config::text AS config, config_hash FROM rule_version WHERE version = ?", version);
        } catch (EmptyResultDataAccessException e) {
            throw new ApiProblem(HttpStatus.NOT_FOUND, "Not Found", "No rule version " + version);
        }
    }
}
