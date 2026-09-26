package com.parallax.application.lab;

import com.parallax.application.error.ApiProblem;
import com.parallax.application.error.ConfigValidationException;
import com.parallax.application.json.CanonicalJson;
import com.parallax.application.ledger.LedgerEntry;
import com.parallax.application.ledger.LedgerKind;
import com.parallax.application.ledger.LedgerSource;
import com.parallax.application.ledger.LedgerWriter;
import com.parallax.application.rules.LiveRule;
import com.parallax.application.rules.LiveRuleService;
import com.parallax.application.security.CurrentUser;
import com.parallax.engine.config.RuleConfigValidator;
import com.parallax.engine.model.BandLimit;
import com.parallax.engine.model.RuleConfig;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * The rule-version lifecycle (SPEC §10, §15): DRAFT → REPLAYED → PROPOSED → LIVE → RETIRED, with
 * maker-checker on promotion (the proposer cannot approve), one open candidate at a time, immutable
 * once used, and a GOVERNANCE ledger row for every promote and rollback. All shapes follow §15.
 */
@Service
public class RuleVersionService {

    private static final String OPEN_CANDIDATE_MESSAGE =
            "Finish or discard the open candidate first — one candidate at a time.";
    private static final String NEEDS_REPLAY_MESSAGE = "Replay the current configuration before proposing";
    private static final String MAKER_CHECKER_MESSAGE = "Maker-checker: the proposer cannot approve";

    private final JdbcTemplate jdbc;
    private final CanonicalJson canonicalJson;
    private final LiveRuleService liveRuleService;
    private final LedgerWriter ledgerWriter;
    private final CurrentUser currentUser;
    private final Clock clock;

    public RuleVersionService(JdbcTemplate jdbc, CanonicalJson canonicalJson, LiveRuleService liveRuleService,
                              LedgerWriter ledgerWriter, CurrentUser currentUser, Clock clock) {
        this.jdbc = jdbc;
        this.canonicalJson = canonicalJson;
        this.liveRuleService = liveRuleService;
        this.ledgerWriter = ledgerWriter;
        this.currentUser = currentUser;
        this.clock = clock;
    }

    // --- list & live ------------------------------------------------------------------------------

    @Transactional(readOnly = true)
    public LabViews.VersionsResponse list() {
        List<Row> rows = jdbc.query(SELECT_ALL, ROW_MAPPER);
        rows.sort((a, b) -> Integer.compare(minor(b.version), minor(a.version))); // newest first

        Map<String, Long> usedBy = usedByCounts();
        List<LabViews.VersionView> items = new ArrayList<>(rows.size());
        for (Row r : rows) {
            items.add(toView(r, usedBy.getOrDefault(r.version, 0L)));
        }
        return new LabViews.VersionsResponse(items, liveVersion(), rollbackTarget());
    }

    @Transactional(readOnly = true)
    public LabViews.LiveView live() {
        LiveRule live = liveRuleService.current();
        LocalDate since = live.since().atOffset(ZoneOffset.UTC).toLocalDate();
        return new LabViews.LiveView(live.version(), since, live.config());
    }

    // --- create -----------------------------------------------------------------------------------

    @Transactional
    public LabViews.VersionView create(CreateVersionRequest request) {
        if (hasOpenCandidate()) {
            throw new ApiProblem(HttpStatus.CONFLICT, "Conflict", OPEN_CANDIDATE_MESSAGE);
        }
        LiveRule live = liveRuleService.current();
        RuleConfig config = (request != null && request.config() != null) ? request.config() : live.config();
        validate(config);

        String version = "v1." + (maxMinor() + 1);
        String note = (request != null && request.note() != null && !request.note().isBlank())
                ? request.note()
                : "Candidate drafted from " + live.version() + ".";
        String configJson = canonicalJson.write(config);
        String configHash = canonicalJson.sha256Hex(configJson);

        jdbc.update("INSERT INTO rule_version (version, status, config, config_hash, note, created_by, created_at)"
                        + " VALUES (?, 'DRAFT', ?::jsonb, ?, ?, ?, ?)",
                version, configJson, configHash, note, currentUser.displayName(), Timestamp.from(now()));
        return view(version);
    }

    // --- edit config / back to draft --------------------------------------------------------------

    @Transactional
    public LabViews.VersionView updateConfig(String version, RuleConfig config) {
        Row row = row(version);
        if (row.firstUsedAt != null) {
            long used = usedBy(version);
            throw new ApiProblem(HttpStatus.CONFLICT, "Conflict",
                    "Version " + version + " is immutable: used by " + used + " decisions");
        }
        if (!"DRAFT".equals(row.status) && !"REPLAYED".equals(row.status)) {
            throw new ApiProblem(HttpStatus.CONFLICT, "Conflict",
                    "Version " + version + " cannot be edited (status " + row.status + ")");
        }
        validate(config);
        String configJson = canonicalJson.write(config);
        String configHash = canonicalJson.sha256Hex(configJson);
        // Editing returns a REPLAYED version to DRAFT and detaches its report (the hash no longer matches).
        jdbc.update("UPDATE rule_version SET config = ?::jsonb, config_hash = ?, status = 'DRAFT' WHERE version = ?",
                configJson, configHash, version);
        return view(version);
    }

    @Transactional
    public LabViews.VersionView backToDraft(String version) {
        Row row = row(version);
        if (!"REPLAYED".equals(row.status)) {
            throw new ApiProblem(HttpStatus.CONFLICT, "Conflict",
                    "Version " + version + " must be REPLAYED to return to DRAFT (status " + row.status + ")");
        }
        jdbc.update("UPDATE rule_version SET status = 'DRAFT' WHERE version = ?", version);
        return view(version);
    }

    // --- delete -----------------------------------------------------------------------------------

    @Transactional
    public void delete(String version) {
        Row row = row(version);
        boolean deletable = ("DRAFT".equals(row.status) || "REPLAYED".equals(row.status))
                && row.firstUsedAt == null;
        if (!deletable) {
            throw new ApiProblem(HttpStatus.CONFLICT, "Conflict",
                    "Only an unused DRAFT or REPLAYED version can be deleted (" + version
                            + " is " + row.status + ")");
        }
        jdbc.update("DELETE FROM replay_flip WHERE job_id IN (SELECT id FROM replay_job WHERE candidate_version = ?)",
                version);
        jdbc.update("DELETE FROM replay_job WHERE candidate_version = ?", version);
        jdbc.update("DELETE FROM rule_version WHERE version = ?", version);
    }

    // --- propose ----------------------------------------------------------------------------------

    @Transactional
    public LabViews.VersionView propose(String version) {
        Row row = row(version);
        if (!"REPLAYED".equals(row.status) || doneReplayJob(version, row.configHash) == null) {
            throw new ApiProblem(HttpStatus.CONFLICT, "Conflict", NEEDS_REPLAY_MESSAGE);
        }
        jdbc.update("UPDATE rule_version SET status = 'PROPOSED', proposed_by = ? WHERE version = ?",
                currentUser.displayName(), version);
        return view(version);
    }

    // --- approve (maker-checker + promote) --------------------------------------------------------

    @Transactional
    public LabViews.VersionView approve(String version) {
        Row row = row(version);
        if (!"PROPOSED".equals(row.status)) {
            throw new ApiProblem(HttpStatus.CONFLICT, "Conflict",
                    "Version " + version + " is not awaiting approval (status " + row.status + ")");
        }
        String approver = currentUser.displayName();
        if (approver.equals(row.proposedBy)) {
            throw new ApiProblem(HttpStatus.FORBIDDEN, "Forbidden", MAKER_CHECKER_MESSAGE);
        }
        String previousLive = liveVersion();
        Timestamp when = Timestamp.from(now());

        if (previousLive != null) {
            jdbc.update("UPDATE rule_version SET status = 'RETIRED', retired_at = ? WHERE version = ?",
                    when, previousLive);
        }
        jdbc.update("UPDATE rule_version SET status = 'LIVE', promoted_at = ?, approved_by = ?, retired_at = NULL"
                        + " WHERE version = ?",
                when, approver, version);

        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("action", "PROMOTE");
        detail.put("version", version);
        detail.put("replaced", previousLive);
        detail.put("proposedBy", row.proposedBy);
        detail.put("approvedBy", approver);
        detail.put("replayJobId", doneReplayJob(version, row.configHash));
        detail.put("note", version + " promoted to LIVE · proposed by " + row.proposedBy
                + " · approved by " + approver + (previousLive == null ? "" : " · replaced " + previousLive));
        appendGovernance(version, detail);

        evictAfterCommit();
        return view(version);
    }

    // --- reject -----------------------------------------------------------------------------------

    @Transactional
    public LabViews.VersionView reject(String version, RejectRequest request) {
        Row row = row(version);
        if (!"PROPOSED".equals(row.status)) {
            throw new ApiProblem(HttpStatus.CONFLICT, "Conflict",
                    "Version " + version + " is not awaiting approval (status " + row.status + ")");
        }
        String rejectionNote = request == null ? null : request.note();
        String noteField = "Rejected by " + currentUser.displayName() + " — revise and replay.";
        jdbc.update("UPDATE rule_version SET status = 'DRAFT', rejection_note = ?, note = ? WHERE version = ?",
                rejectionNote, noteField, version);
        return view(version);
    }

    // --- rollback ---------------------------------------------------------------------------------

    @Transactional
    public LabViews.RollbackView rollback() {
        String target = rollbackTarget();
        if (target == null) {
            throw new ApiProblem(HttpStatus.CONFLICT, "Conflict", "No previous LIVE version to roll back to");
        }
        String previousLive = liveVersion();
        Timestamp when = Timestamp.from(now());

        if (previousLive != null) {
            jdbc.update("UPDATE rule_version SET status = 'RETIRED', retired_at = ? WHERE version = ?",
                    when, previousLive);
        }
        jdbc.update("UPDATE rule_version SET status = 'LIVE', promoted_at = ?, retired_at = NULL WHERE version = ?",
                when, target);

        String by = currentUser.displayName();
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("action", "ROLLBACK");
        detail.put("from", previousLive);
        detail.put("to", target);
        detail.put("by", by);
        detail.put("note", "Rollback: " + previousLive + " → " + target + " by " + by);
        appendGovernance(target, detail);

        evictAfterCommit();
        return new LabViews.RollbackView(previousLive, target);
    }

    // --- compare ----------------------------------------------------------------------------------

    @Transactional(readOnly = true)
    public LabViews.CompareView compare(String a, String b) {
        RuleConfig configA = configOf(a);
        RuleConfig configB = configOf(b);
        Map<String, Object> flatA = flatten(configA);
        Map<String, Object> flatB = flatten(configB);

        LinkedHashSet<String> fields = new LinkedHashSet<>(flatA.keySet());
        fields.addAll(flatB.keySet());

        List<LabViews.Difference> differences = new ArrayList<>();
        for (String field : fields) {
            Object av = flatA.get(field);
            Object bv = flatB.get(field);
            if (!Objects.equals(av, bv)) {
                differences.add(new LabViews.Difference(field, av, bv));
            }
        }
        return new LabViews.CompareView(a, b, differences);
    }

    // --- governance ledger helper -----------------------------------------------------------------

    private void appendGovernance(String ruleVersion, Map<String, Object> detail) {
        ledgerWriter.append(LedgerEntry.builder(LedgerKind.GOVERNANCE, LedgerSource.LIVE)
                .ruleVersion(ruleVersion)
                .governanceDetail(detail)
                .build());
    }

    private void evictAfterCommit() {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                liveRuleService.evict();
            }
        });
    }

    // --- flatten for compare ----------------------------------------------------------------------

    private static Map<String, Object> flatten(RuleConfig c) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("approveCutoff", c.approveCutoff());
        m.put("referCutoff", c.referCutoff());
        m.put("minPayPct", c.minPayPct());
        m.put("atpShare", c.atpShare());
        m.put("livingCost", c.livingCost());
        m.put("minLimit", c.minLimit());
        List<BandLimit> bands = c.bandLimits();
        for (int i = 0; i < bands.size(); i++) {
            m.put("bandLimits[" + i + "].minScore", bands.get(i).minScore());
            m.put("bandLimits[" + i + "].limit", bands.get(i).limit());
        }
        putList(m, "utilPts", c.utilPts());
        putList(m, "inqPts", c.inqPts());
        putList(m, "delqPts", c.delqPts());
        putList(m, "tradelinePts", c.tradelinePts());
        putList(m, "fileAgePts", c.fileAgePts());
        putList(m, "incomePts", c.incomePts());
        m.put("ccf", c.ccf());
        m.put("lgd", c.lgd());
        return m;
    }

    private static void putList(Map<String, Object> m, String name, List<Integer> values) {
        for (int i = 0; i < values.size(); i++) {
            m.put(name + "[" + i + "]", values.get(i));
        }
    }

    // --- lookups ----------------------------------------------------------------------------------

    private boolean hasOpenCandidate() {
        Integer open = jdbc.queryForObject(
                "SELECT count(*) FROM rule_version WHERE status IN ('DRAFT','REPLAYED','PROPOSED')", Integer.class);
        return open != null && open > 0;
    }

    private int maxMinor() {
        List<String> versions = jdbc.queryForList("SELECT version FROM rule_version", String.class);
        int max = 0;
        for (String v : versions) {
            max = Math.max(max, minor(v));
        }
        return max;
    }

    private static int minor(String version) {
        int dot = version.lastIndexOf('.');
        try {
            return dot < 0 ? 0 : Integer.parseInt(version.substring(dot + 1));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private String liveVersion() {
        return jdbc.query("SELECT version FROM rule_version WHERE status = 'LIVE'",
                rs -> rs.next() ? rs.getString(1) : null);
    }

    private String rollbackTarget() {
        return jdbc.query("SELECT version FROM rule_version WHERE status = 'RETIRED'"
                        + " AND (promoted_at IS NOT NULL OR version = 'v1.2')"
                        + " ORDER BY retired_at DESC NULLS LAST LIMIT 1",
                rs -> rs.next() ? rs.getString(1) : null);
    }

    /** The newest DONE replay for this version's current config, or null (used by propose/promote). */
    private String doneReplayJob(String version, String configHash) {
        return jdbc.query("SELECT id FROM replay_job WHERE status = 'DONE' AND candidate_version = ?"
                        + " AND candidate_config_hash = ? ORDER BY finished_at DESC NULLS LAST LIMIT 1",
                rs -> rs.next() ? rs.getString(1) : null, version, configHash);
    }

    /** The newest replay for this version's current config, any status (the card's latestReplayJobId). */
    private String latestReplayJob(String version, String configHash) {
        return jdbc.query("SELECT id FROM replay_job WHERE candidate_version = ? AND candidate_config_hash = ?"
                        + " ORDER BY created_at DESC LIMIT 1",
                rs -> rs.next() ? rs.getString(1) : null, version, configHash);
    }

    private Map<String, Long> usedByCounts() {
        Map<String, Long> counts = new LinkedHashMap<>();
        jdbc.query("SELECT rule_version, count(*) AS n FROM decision_ledger"
                        + " WHERE kind IN ('DECISION','REDECISION','OVERRIDE') AND rule_version IS NOT NULL"
                        + " GROUP BY rule_version",
                rs -> {
                    counts.put(rs.getString("rule_version"), rs.getLong("n"));
                });
        return counts;
    }

    private long usedBy(String version) {
        Long n = jdbc.queryForObject("SELECT count(*) FROM decision_ledger"
                        + " WHERE kind IN ('DECISION','REDECISION','OVERRIDE') AND rule_version = ?",
                Long.class, version);
        return n == null ? 0 : n;
    }

    private RuleConfig configOf(String version) {
        try {
            String json = jdbc.queryForObject("SELECT config::text FROM rule_version WHERE version = ?",
                    String.class, version);
            return canonicalJson.read(json, RuleConfig.class);
        } catch (EmptyResultDataAccessException e) {
            throw new ApiProblem(HttpStatus.NOT_FOUND, "Not Found", "No rule version " + version);
        }
    }

    private void validate(RuleConfig config) {
        List<String> errors = RuleConfigValidator.validate(config);
        if (!errors.isEmpty()) {
            throw new ConfigValidationException("Invalid rule configuration", errors);
        }
    }

    private Instant now() {
        return Instant.now(clock);
    }

    private Row row(String version) {
        try {
            return jdbc.queryForObject(SELECT_ONE, ROW_MAPPER, version);
        } catch (EmptyResultDataAccessException e) {
            throw new ApiProblem(HttpStatus.NOT_FOUND, "Not Found", "No rule version " + version);
        }
    }

    private LabViews.VersionView view(String version) {
        return toView(row(version), usedBy(version));
    }

    private LabViews.VersionView toView(Row r, long usedBy) {
        return new LabViews.VersionView(r.version, r.status, r.note, r.createdBy, r.createdAt,
                r.proposedBy, r.approvedBy, r.promotedAt, usedBy,
                latestReplayJob(r.version, r.configHash), canonicalJson.read(r.configJson, RuleConfig.class), false);
    }

    // --- row projection ---------------------------------------------------------------------------

    private static final String COLUMNS = "version, status, note, created_by, created_at, proposed_by,"
            + " approved_by, promoted_at, retired_at, first_used_at, config::text AS config, config_hash";
    private static final String SELECT_ALL = "SELECT " + COLUMNS + " FROM rule_version";
    private static final String SELECT_ONE = SELECT_ALL + " WHERE version = ?";

    private static final org.springframework.jdbc.core.RowMapper<Row> ROW_MAPPER = (rs, i) -> new Row(
            rs.getString("version"),
            rs.getString("status"),
            rs.getString("note"),
            rs.getString("created_by"),
            instant(rs.getObject("created_at", OffsetDateTime.class)),
            rs.getString("proposed_by"),
            rs.getString("approved_by"),
            instant(rs.getObject("promoted_at", OffsetDateTime.class)),
            instant(rs.getObject("retired_at", OffsetDateTime.class)),
            instant(rs.getObject("first_used_at", OffsetDateTime.class)),
            rs.getString("config"),
            trim(rs.getString("config_hash")));

    private static Instant instant(OffsetDateTime odt) {
        return odt == null ? null : odt.toInstant();
    }

    private static String trim(String value) {
        return value == null ? null : value.trim();
    }

    private record Row(String version, String status, String note, String createdBy, Instant createdAt,
                       String proposedBy, String approvedBy, Instant promotedAt, Instant retiredAt,
                       Instant firstUsedAt, String configJson, String configHash) {
    }
}
