package com.parallax.application.lab;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.parallax.application.error.ApiProblem;
import com.parallax.application.ledger.LedgerReader;
import com.parallax.application.ledger.LedgerRecord;
import com.parallax.application.rules.LiveRule;
import com.parallax.application.rules.LiveRuleService;
import com.parallax.engine.model.Decision;
import com.parallax.engine.model.EngineInput;
import com.parallax.engine.model.RuleConfig;
import com.parallax.engine.scoring.DecisionEngine;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;

/** Read model for the Strategy Lab replay job, flips page and flip detail (SPEC §15). */
@Service
@Transactional(readOnly = true)
public class ReplayQueryService {

    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;
    private final LedgerReader ledgerReader;
    private final LiveRuleService liveRuleService;

    public ReplayQueryService(JdbcTemplate jdbc, ObjectMapper objectMapper, LedgerReader ledgerReader,
                              LiveRuleService liveRuleService) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
        this.ledgerReader = ledgerReader;
        this.liveRuleService = liveRuleService;
    }

    public ReplayViews.JobView job(String jobId) {
        ReplayViews.JobView view = jdbc.query("SELECT id, candidate_version, baseline_version, status, progress,"
                        + " total, load_ms, evaluate_ms, total_ms, report::text AS report, error"
                        + " FROM replay_job WHERE id = ?",
                rs -> rs.next() ? new ReplayViews.JobView(
                        rs.getString("id"),
                        rs.getString("candidate_version"),
                        rs.getString("baseline_version"),
                        rs.getString("status"),
                        rs.getObject("progress", Integer.class),
                        rs.getObject("total", Integer.class),
                        rs.getObject("load_ms", Long.class),
                        rs.getObject("evaluate_ms", Long.class),
                        rs.getObject("total_ms", Long.class),
                        readJson(rs.getString("report")),
                        rs.getString("error")) : null,
                jobId);
        if (view == null) {
            throw new ApiProblem(HttpStatus.NOT_FOUND, "Not Found", "No replay job " + jobId);
        }
        return view;
    }

    public ReplayViews.FlipPage flips(String jobId, int page, int size) {
        long total = Objects.requireNonNullElse(jdbc.queryForObject(
                "SELECT count(*) FROM replay_flip WHERE job_id = ?", Long.class, jobId), 0L);
        List<ReplayViews.FlipItem> items = jdbc.query(
                "SELECT ledger_seq, application_public_id, baseline_outcome, candidate_outcome, baseline_score,"
                        + " candidate_score, baseline_limit, candidate_limit, candidate_reasons::text AS reasons,"
                        + " observed FROM replay_flip WHERE job_id = ? ORDER BY ledger_seq LIMIT ? OFFSET ?",
                (rs, i) -> new ReplayViews.FlipItem(
                        rs.getLong("ledger_seq"),
                        rs.getString("application_public_id"),
                        new ReplayViews.SideOutcome(rs.getString("baseline_outcome"),
                                rs.getObject("baseline_score", Integer.class),
                                rs.getObject("baseline_limit", Integer.class)),
                        new ReplayViews.SideOutcome(rs.getString("candidate_outcome"),
                                rs.getObject("candidate_score", Integer.class),
                                rs.getObject("candidate_limit", Integer.class)),
                        readStringList(rs.getString("reasons")),
                        rs.getBoolean("observed")),
                jobId, size, (long) page * size);
        return new ReplayViews.FlipPage(items, page, size, total);
    }

    public ReplayViews.FlipDetail flipDetail(String jobId, long seq) {
        String candidateVersion;
        try {
            candidateVersion = jdbc.queryForObject(
                    "SELECT candidate_version FROM replay_job WHERE id = ?", String.class, jobId);
        } catch (EmptyResultDataAccessException e) {
            throw new ApiProblem(HttpStatus.NOT_FOUND, "Not Found", "No replay job " + jobId);
        }

        List<LedgerRecord> rows = jdbc.query(LedgerReader.SELECT + " WHERE dl.seq = ?",
                ledgerReader.rowMapper(), seq);
        if (rows.isEmpty()) {
            throw new ApiProblem(HttpStatus.NOT_FOUND, "Not Found", "No ledger row #" + seq);
        }
        LedgerRecord record = rows.get(0);
        if (!(record.engineInput() instanceof EngineInput input)) {
            throw new ApiProblem(HttpStatus.UNPROCESSABLE_ENTITY, "Unprocessable Entity",
                    "Ledger row #" + seq + " has no full engine input");
        }

        LiveRule live = liveRuleService.current();
        RuleConfig candidateConfig = liveRuleService.configOf(candidateVersion);
        Decision baseline = DecisionEngine.evaluate(input, live.config());
        Decision candidate = DecisionEngine.evaluate(input, candidateConfig);
        boolean observed = observed(record.applicationDbId());

        return new ReplayViews.FlipDetail(seq, record.applicationPublicId(), input, observed,
                new ReplayViews.SideDecision(live.version(), live.config().approveCutoff(),
                        live.config().referCutoff(), baseline),
                new ReplayViews.SideDecision(candidateVersion, candidateConfig.approveCutoff(),
                        candidateConfig.referCutoff(), candidate));
    }

    private boolean observed(Long applicationDbId) {
        if (applicationDbId == null) {
            return false;
        }
        Boolean simulated = jdbc.query("SELECT simulated FROM loan_outcome WHERE application_id = ?",
                rs -> rs.next() ? rs.getBoolean("simulated") : null, applicationDbId);
        return simulated != null && !simulated;
    }

    private Object readJson(String json) {
        if (json == null) {
            return null;
        }
        try {
            return objectMapper.readTree(json);
        } catch (Exception e) {
            throw new IllegalStateException("Corrupt replay report JSON", e);
        }
    }

    @SuppressWarnings("unchecked")
    private List<String> readStringList(String json) {
        if (json == null) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, List.class);
        } catch (Exception e) {
            throw new IllegalStateException("Corrupt candidate reasons JSON", e);
        }
    }
}
