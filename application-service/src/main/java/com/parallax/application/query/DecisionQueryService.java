package com.parallax.application.query;

import com.parallax.application.domain.ApplicationEntity;
import com.parallax.application.domain.ApplicationRepository;
import com.parallax.application.error.ApiProblem;
import com.parallax.application.ledger.LedgerKind;
import com.parallax.application.ledger.LedgerReader;
import com.parallax.application.ledger.LedgerRecord;
import com.parallax.application.pii.DataCipher;
import com.parallax.application.rules.LiveRuleService;
import com.parallax.engine.model.BandLimit;
import com.parallax.engine.model.Decision;
import com.parallax.engine.model.EngineInput;
import com.parallax.engine.model.ReasonCode;
import com.parallax.engine.model.RuleConfig;
import com.parallax.engine.scoring.DecisionEngine;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Read model for the Decisions and Decision-detail screens (SPEC §15). */
@Service
@Transactional(readOnly = true)
public class DecisionQueryService {

    private static final List<String> UNTRUSTED = List.of("address");

    private final NamedParameterJdbcTemplate namedJdbc;
    private final JdbcTemplate jdbc;
    private final LedgerReader ledgerReader;
    private final ApplicationRepository applicationRepository;
    private final DataCipher dataCipher;
    private final LiveRuleService liveRuleService;
    private final DisplayNamePolicy displayNamePolicy;
    private final AdverseActionNotice adverseActionNotice;

    public DecisionQueryService(NamedParameterJdbcTemplate namedJdbc, JdbcTemplate jdbc,
                                LedgerReader ledgerReader, ApplicationRepository applicationRepository,
                                DataCipher dataCipher, LiveRuleService liveRuleService,
                                DisplayNamePolicy displayNamePolicy, AdverseActionNotice adverseActionNotice) {
        this.namedJdbc = namedJdbc;
        this.jdbc = jdbc;
        this.ledgerReader = ledgerReader;
        this.applicationRepository = applicationRepository;
        this.dataCipher = dataCipher;
        this.liveRuleService = liveRuleService;
        this.displayNamePolicy = displayNamePolicy;
        this.adverseActionNotice = adverseActionNotice;
    }

    // --- list -----------------------------------------------------------------------------------

    private static final String CURRENT_ROWS = """
            SELECT DISTINCT ON (dl.application_id)
                   dl.application_id, dl.seq, dl.kind, dl.outcome, dl.score, dl.credit_limit,
                   dl.rule_version, dl.created_at, a.public_id, a.product, a.name_enc, a.name_masked
            FROM decision_ledger dl JOIN application a ON a.id = dl.application_id
            WHERE (:source = 'ALL' OR dl.source = :source)
              AND (:q IS NULL OR a.public_id ILIKE :qLike)
            ORDER BY dl.application_id, dl.seq DESC
            """;

    public ApplicationViews.ListResponse list(String role, String outcome, String q, String source,
                                              int page, int size) {
        String effectiveSource = (source == null || source.isBlank()) ? "LIVE" : source.toUpperCase();
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("source", effectiveSource)
                .addValue("q", (q == null || q.isBlank()) ? null : q, java.sql.Types.VARCHAR)
                .addValue("qLike", (q == null || q.isBlank()) ? null : q + "%", java.sql.Types.VARCHAR)
                .addValue("outcome", (outcome == null || outcome.isBlank()) ? null : outcome, java.sql.Types.VARCHAR);

        ApplicationViews.Counts counts = counts(params);

        long total = Objects.requireNonNullElse(namedJdbc.queryForObject(
                "SELECT count(*) FROM (" + CURRENT_ROWS + ") current WHERE (:outcome IS NULL OR current.outcome = :outcome)",
                params, Long.class), 0L);

        params.addValue("size", size).addValue("offset", (long) page * size);
        List<ApplicationViews.ListItem> items = namedJdbc.query(
                "SELECT * FROM (" + CURRENT_ROWS + ") current WHERE (:outcome IS NULL OR current.outcome = :outcome)"
                        + " ORDER BY current.created_at DESC, current.seq DESC LIMIT :size OFFSET :offset",
                params, (rs, i) -> new ApplicationViews.ListItem(
                        rs.getString("public_id"),
                        displayName(role, rs.getBytes("name_enc"), rs.getString("name_masked")),
                        rs.getString("product"),
                        rs.getObject("score", Integer.class),
                        rs.getString("outcome"),
                        rs.getObject("credit_limit", Integer.class),
                        rs.getString("rule_version"),
                        rs.getString("kind"),
                        rs.getObject("created_at", java.time.OffsetDateTime.class).toInstant()));

        return new ApplicationViews.ListResponse(items, page, size, total, counts);
    }

    private ApplicationViews.Counts counts(MapSqlParameterSource params) {
        Map<String, Long> byOutcome = new java.util.HashMap<>();
        namedJdbc.query("SELECT current.outcome AS outcome, count(*) AS n FROM (" + CURRENT_ROWS
                + ") current GROUP BY current.outcome", params, rs -> {
            byOutcome.put(rs.getString("outcome"), rs.getLong("n"));
        });
        long all = byOutcome.values().stream().mapToLong(Long::longValue).sum();
        return new ApplicationViews.Counts(all,
                byOutcome.getOrDefault("APPROVED", 0L),
                byOutcome.getOrDefault("REFER", 0L),
                byOutcome.getOrDefault("DECLINED", 0L));
    }

    // --- detail ---------------------------------------------------------------------------------

    public ApplicationViews.Detail detail(String role, String publicId) {
        ApplicationEntity app = applicationRepository.findByPublicId(publicId)
                .orElseThrow(() -> new ApiProblem(HttpStatus.NOT_FOUND, "Not Found",
                        "No application " + publicId));

        List<LedgerRecord> trail = jdbc.query(LedgerReader.SELECT + " WHERE dl.application_id = ? ORDER BY dl.seq",
                ledgerReader.rowMapper(), app.getId());

        LedgerRecord current = trail.isEmpty() ? null : trail.get(trail.size() - 1);
        LedgerRecord base = trail.stream().filter(r -> r.kind() != LedgerKind.OVERRIDE)
                .reduce((a, b) -> b).orElse(null);

        byte[] ssnEnc = jdbc.queryForObject("SELECT ssn_enc FROM application WHERE id = ?", byte[].class, app.getId());

        return new ApplicationViews.Detail(
                app.getPublicId(),
                displayNamePolicy.fullName(role) ? app.getName() : app.getNameMasked(),
                app.getProduct(),
                app.getStatus().name(),
                app.getCreatedAt(),
                "***-**-" + app.getSsnLast4(),
                displayNamePolicy.ssnPreviewVisible(role) ? dataCipher.preview(ssnEnc) : null,
                displayNamePolicy.addressVisible(role) ? app.getAddress() : null,
                UNTRUSTED,
                current == null ? null : currentView(current),
                base == null ? null : baseView(base),
                breakdown(base),
                bureau(base),
                trail.stream().map(this::trailItem).toList(),
                shadow(base));
    }

    private ApplicationViews.Shadow shadow(LedgerRecord base) {
        if (base == null) {
            return null;
        }
        return jdbc.query("SELECT version, outcome, score, credit_limit, agrees FROM shadow_result"
                        + " WHERE ledger_seq = ?",
                rs -> rs.next() ? new ApplicationViews.Shadow(rs.getString("version"), rs.getString("outcome"),
                        rs.getObject("score", Integer.class), rs.getObject("credit_limit", Integer.class),
                        rs.getBoolean("agrees")) : null,
                base.seq());
    }

    private ApplicationViews.Current currentView(LedgerRecord r) {
        return new ApplicationViews.Current(r.seq(), r.kind().name(), r.outcome(), r.creditLimit(), overrideOf(r));
    }

    private ApplicationViews.Base baseView(LedgerRecord r) {
        return new ApplicationViews.Base(r.seq(), r.kind().name(), r.ruleVersion(), r.scorecardVersion(),
                r.engineVersion(), r.outcome(), r.score(), r.creditLimit(),
                reasons(r.reasonCodes()), fraudFlags(r.fraudFlags()), r.atpMax(), r.engineInput(), r.createdAt());
    }

    private ApplicationViews.Breakdown breakdown(LedgerRecord base) {
        if (base == null || !(base.engineInput() instanceof EngineInput input)) {
            return null; // B01 rows (partial input) have no breakdown
        }
        RuleConfig config = liveRuleService.configOf(base.ruleVersion());
        Decision decision = DecisionEngine.evaluate(input, config);
        List<ApplicationViews.PolicyCheck> policyChecks = decision.policyChecks().stream()
                .map(p -> new ApplicationViews.PolicyCheck(p.code().name(), p.name(), p.passed(), p.detail()))
                .toList();
        List<ApplicationViews.ScorePart> scoreParts = decision.scoreParts().stream()
                .map(s -> new ApplicationViews.ScorePart(s.attribute(), s.value(), s.band(), s.points(),
                        s.maxPoints(), s.pointsLost(), s.code().name()))
                .toList();
        Integer bandLimit = "APPROVED".equals(base.outcome()) ? bandLimit(config, decision.score()) : null;
        return new ApplicationViews.Breakdown(policyChecks, scoreParts,
                config.approveCutoff(), config.referCutoff(), bandLimit);
    }

    private ApplicationViews.Bureau bureau(LedgerRecord base) {
        if (base == null || base.bureauPullId() == null) {
            return null;
        }
        return jdbc.query("SELECT pull_type, raw_xml FROM bureau_pull WHERE id = ?",
                rs -> rs.next() ? new ApplicationViews.Bureau(base.bureauPullId(), rs.getString("pull_type"),
                        Boolean.TRUE.equals(base.bureauReused()), rs.getString("raw_xml")) : null,
                base.bureauPullId());
    }

    private ApplicationViews.TrailItem trailItem(LedgerRecord r) {
        return new ApplicationViews.TrailItem(r.seq(), r.kind().name(), r.outcome(), r.ruleVersion(),
                r.createdAt(), r.prevHash(), r.hash(), overrideOf(r));
    }

    private ApplicationViews.Override overrideOf(LedgerRecord r) {
        if (r.kind() != LedgerKind.OVERRIDE || r.overrideDetail() == null) {
            return null;
        }
        Map<String, Object> d = r.overrideDetail();
        return new ApplicationViews.Override(str(d.get("by")), str(d.get("code")),
                str(d.get("codeDescription")), str(d.get("note")));
    }

    // --- reproduce ------------------------------------------------------------------------------

    public ReproduceView reproduce(long seq) {
        LedgerRecord row = loadRow(seq);
        if (row.kind() == LedgerKind.OVERRIDE && row.linkedSeq() != null) {
            LedgerRecord baseRow = loadRow(row.linkedSeq());
            ReproduceView base = reproduceEngineRow(baseRow);
            return new ReproduceView(seq, baseRow.ruleVersion(), base.identical(),
                    "Reproduced from base ledger #" + baseRow.seq() + " (override)", base.stored(), base.recomputed());
        }
        return reproduceEngineRow(row);
    }

    private ReproduceView reproduceEngineRow(LedgerRecord row) {
        ReproduceView.Outcome stored = new ReproduceView.Outcome(row.outcome(), row.score(), row.creditLimit(),
                row.reasonCodes());
        if (!(row.engineInput() instanceof EngineInput input)) {
            return new ReproduceView(row.seq(), row.ruleVersion(), null,
                    "No engine evaluation (bureau unavailable)", stored, null);
        }
        Decision decision = DecisionEngine.evaluate(input, liveRuleService.configOf(row.ruleVersion()));
        List<String> recomputedReasons = decision.reasonCodes().stream().map(Enum::name).toList();
        ReproduceView.Outcome recomputed = new ReproduceView.Outcome(decision.outcome().name(),
                decision.score(), decision.creditLimit(), recomputedReasons);
        boolean identical = Objects.equals(stored.outcome(), recomputed.outcome())
                && Objects.equals(stored.score(), recomputed.score())
                && Objects.equals(stored.creditLimit(), recomputed.creditLimit())
                && Objects.equals(stored.reasonCodes(), recomputed.reasonCodes());
        return new ReproduceView(row.seq(), row.ruleVersion(), identical,
                identical ? "Reproduced identically" : "Reproduction differs from the stored decision",
                stored, recomputed);
    }

    // --- adverse action notice ------------------------------------------------------------------

    public String adverseActionNotice(String role, String publicId) {
        ApplicationEntity app = applicationRepository.findByPublicId(publicId)
                .orElseThrow(() -> new ApiProblem(HttpStatus.NOT_FOUND, "Not Found", "No application " + publicId));
        List<LedgerRecord> trail = jdbc.query(LedgerReader.SELECT + " WHERE dl.application_id = ? ORDER BY dl.seq",
                ledgerReader.rowMapper(), app.getId());
        LedgerRecord current = trail.isEmpty() ? null : trail.get(trail.size() - 1);
        if (current == null || !"DECLINED".equals(current.outcome())) {
            throw new ApiProblem(HttpStatus.NOT_FOUND, "Not Found", "No adverse action notice for " + publicId);
        }
        LedgerRecord base = trail.stream().filter(r -> r.kind() != LedgerKind.OVERRIDE)
                .reduce((a, b) -> b).orElseThrow();
        List<String> descriptions = new ArrayList<>();
        if (base.reasonCodes() != null) {
            for (String c : base.reasonCodes()) {
                ReasonCode rc = ReasonCode.valueOf(c);
                if (rc.applicantFacing()) {
                    descriptions.add(rc.description());
                }
            }
        }
        if (descriptions.isEmpty()) {
            throw new ApiProblem(HttpStatus.NOT_FOUND, "Not Found", "No applicant-facing reason for " + publicId);
        }
        String displayName = displayNamePolicy.fullName(role) ? app.getName() : app.getNameMasked();
        java.time.LocalDate date = base.createdAt().atZone(java.time.ZoneOffset.UTC).toLocalDate();
        return adverseActionNotice.render(displayName, app.getProduct(), descriptions, date,
                base.seq(), base.ruleVersion());
    }

    private LedgerRecord loadRow(long seq) {
        List<LedgerRecord> rows = jdbc.query(LedgerReader.SELECT + " WHERE dl.seq = ?",
                ledgerReader.rowMapper(), seq);
        if (rows.isEmpty()) {
            throw new ApiProblem(HttpStatus.NOT_FOUND, "Not Found", "No ledger row #" + seq);
        }
        return rows.get(0);
    }

    // --- helpers --------------------------------------------------------------------------------

    private String displayName(String role, byte[] nameEnc, String nameMasked) {
        return displayNamePolicy.fullName(role) ? dataCipher.decrypt(nameEnc) : nameMasked;
    }

    private List<ApplicationViews.Reason> reasons(List<String> codes) {
        if (codes == null) {
            return List.of();
        }
        List<ApplicationViews.Reason> out = new ArrayList<>();
        for (String c : codes) {
            ReasonCode rc = ReasonCode.valueOf(c);
            out.add(new ApplicationViews.Reason(rc.name(), rc.description(), rc.applicantFacing()));
        }
        return out;
    }

    private List<ApplicationViews.Fraud> fraudFlags(List<String> codes) {
        if (codes == null) {
            return List.of();
        }
        return codes.stream().map(c -> {
            ReasonCode rc = ReasonCode.valueOf(c);
            return new ApplicationViews.Fraud(rc.name(), rc.description());
        }).toList();
    }

    private static Integer bandLimit(RuleConfig config, int score) {
        for (BandLimit band : config.bandLimits()) {
            if (score >= band.minScore()) {
                return band.limit();
            }
        }
        return null;
    }

    private static String str(Object value) {
        return value == null ? null : value.toString();
    }
}
