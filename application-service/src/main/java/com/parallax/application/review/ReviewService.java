package com.parallax.application.review;

import com.parallax.application.bureau.BureauReport;
import com.parallax.application.bureau.BureauService;
import com.parallax.application.domain.ApplicationEntity;
import com.parallax.application.domain.ApplicationRepository;
import com.parallax.application.domain.ApplicationStatus;
import com.parallax.application.error.ApiProblem;
import com.parallax.application.feature.DerivedFeatures;
import com.parallax.application.feature.EngineInputMapper;
import com.parallax.application.feature.FeatureService;
import com.parallax.application.intake.ApplicationRequest;
import com.parallax.application.jobs.ApplicationForms;
import com.parallax.application.json.CanonicalJson;
import com.parallax.application.ledger.LedgerEntry;
import com.parallax.application.ledger.LedgerKind;
import com.parallax.application.ledger.LedgerReader;
import com.parallax.application.ledger.LedgerRecord;
import com.parallax.application.ledger.LedgerSource;
import com.parallax.application.ledger.LedgerWriter;
import com.parallax.application.pii.DataCipher;
import com.parallax.application.query.DisplayNamePolicy;
import com.parallax.application.rules.LiveRule;
import com.parallax.application.rules.LiveRuleService;
import com.parallax.application.security.CurrentUser;
import com.parallax.engine.model.EngineInput;
import com.parallax.engine.model.Usd;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The review queue, overrides and override statistics (SPEC §4, §6, §15). A REFER decision (score
 * band, fraud flags or a bureau outage) needs a human: an underwriter records an OVERRIDE row while
 * the original decision is never modified. ENGINE_FAILED_MANUAL applications, which have no ledger
 * row, are reviewed by rebuilding their EngineInput first.
 */
@Service
public class ReviewService {

    private static final int SUGGESTED_LIMIT_CAP = 2000;

    /** Candidate applications for the queue: current outcome REFER, or awaiting manual review. */
    private static final String QUEUE_SQL = """
            SELECT a.id, a.public_id, a.status, a.created_at, a.name_enc, a.name_masked,
                   dl.seq AS base_seq, dl.outcome, dl.score, dl.reason_codes, dl.fraud_flags, dl.atp_max
            FROM application a
            LEFT JOIN LATERAL (
                SELECT seq, outcome, score, reason_codes, fraud_flags, atp_max
                FROM decision_ledger d WHERE d.application_id = a.id ORDER BY d.seq DESC LIMIT 1
            ) dl ON true
            WHERE (a.status IN ('DECIDED','BUREAU_UNAVAILABLE') AND dl.outcome = 'REFER')
               OR a.status = 'ENGINE_FAILED_MANUAL'
            ORDER BY a.created_at ASC, a.id ASC
            """;

    /** REFER decisions counted into the four bands by their score (null score → "no score"). */
    private static final String REFERS_SQL = """
            SELECT
              count(*) FILTER (WHERE score IS NOT NULL AND score < 620)  AS lt620,
              count(*) FILTER (WHERE score >= 620 AND score < 680)       AS mid,
              count(*) FILTER (WHERE score >= 680)                       AS hi,
              count(*) FILTER (WHERE score IS NULL)                      AS none
            FROM decision_ledger
            WHERE kind IN ('DECISION','REDECISION') AND outcome = 'REFER'
            """;

    /** OVERRIDE→APPROVED rows counted into the band of their linked base row's score. */
    private static final String OVERRIDDEN_SQL = """
            SELECT
              count(*) FILTER (WHERE base.score IS NOT NULL AND base.score < 620) AS lt620,
              count(*) FILTER (WHERE base.score >= 620 AND base.score < 680)      AS mid,
              count(*) FILTER (WHERE base.score >= 680)                           AS hi,
              count(*) FILTER (WHERE base.score IS NULL)                          AS none
            FROM decision_ledger o
            LEFT JOIN decision_ledger base ON base.seq = o.linked_seq
            WHERE o.kind = 'OVERRIDE' AND o.outcome = 'APPROVED'
            """;

    private final JdbcTemplate jdbc;
    private final LedgerReader ledgerReader;
    private final LedgerWriter ledgerWriter;
    private final ApplicationRepository applicationRepository;
    private final DataCipher dataCipher;
    private final DisplayNamePolicy displayNamePolicy;
    private final CanonicalJson canonicalJson;
    private final CurrentUser currentUser;
    private final BureauService bureauService;
    private final FeatureService featureService;
    private final EngineInputMapper engineInputMapper;
    private final LiveRuleService liveRuleService;
    private final Clock clock;

    public ReviewService(JdbcTemplate jdbc, LedgerReader ledgerReader, LedgerWriter ledgerWriter,
                         ApplicationRepository applicationRepository, DataCipher dataCipher,
                         DisplayNamePolicy displayNamePolicy, CanonicalJson canonicalJson,
                         CurrentUser currentUser, BureauService bureauService, FeatureService featureService,
                         EngineInputMapper engineInputMapper, LiveRuleService liveRuleService, Clock clock) {
        this.jdbc = jdbc;
        this.ledgerReader = ledgerReader;
        this.ledgerWriter = ledgerWriter;
        this.applicationRepository = applicationRepository;
        this.dataCipher = dataCipher;
        this.displayNamePolicy = displayNamePolicy;
        this.canonicalJson = canonicalJson;
        this.currentUser = currentUser;
        this.bureauService = bureauService;
        this.featureService = featureService;
        this.engineInputMapper = engineInputMapper;
        this.liveRuleService = liveRuleService;
        this.clock = clock;
    }

    // --- queue ----------------------------------------------------------------------------------

    @Transactional(readOnly = true)
    public List<ReviewViews.QueueItem> queue(String role) {
        return jdbc.query(QUEUE_SQL, (rs, i) -> {
            boolean engineManual = "ENGINE_FAILED_MANUAL".equals(rs.getString("status"));
            List<String> reasonCodes = parseList(rs.getString("reason_codes"));
            List<String> fraudFlags = parseList(rs.getString("fraud_flags"));
            Integer atpMax = rs.getObject("atp_max", Integer.class);
            String reasonKind;
            if (engineManual) {
                reasonKind = "engine";
            } else if (reasonCodes.contains("B01")) {
                reasonKind = "bureau";
            } else if (!fraudFlags.isEmpty()) {
                reasonKind = "fraud";
            } else {
                reasonKind = "score";
            }
            return new ReviewViews.QueueItem(
                    rs.getString("public_id"),
                    displayName(role, rs.getBytes("name_enc"), rs.getString("name_masked")),
                    rs.getObject("score", Integer.class),
                    reasonKind,
                    reasonCodes,
                    fraudFlags,
                    atpMax,
                    atpMax == null ? SUGGESTED_LIMIT_CAP : Math.min(atpMax, SUGGESTED_LIMIT_CAP),
                    rs.getObject("base_seq", Long.class),
                    rs.getObject("created_at", OffsetDateTime.class).toInstant());
        });
    }

    // --- record an override -----------------------------------------------------------------------

    /** Records a manual decision as an OVERRIDE row and moves the application to REVIEWED (one transaction). */
    @Transactional
    public ReviewViews.RecordResult record(String publicId, ReviewRequest request) {
        ApplicationEntity app = applicationRepository.findByPublicId(publicId)
                .orElseThrow(() -> new ApiProblem(HttpStatus.NOT_FOUND, "Not Found", "No application " + publicId));

        List<LedgerRecord> trail = jdbc.query(
                LedgerReader.SELECT + " WHERE dl.application_id = ? ORDER BY dl.seq",
                ledgerReader.rowMapper(), app.getId());
        LedgerRecord current = trail.isEmpty() ? null : trail.get(trail.size() - 1);
        LedgerRecord base = trail.stream().filter(r -> r.kind() != LedgerKind.OVERRIDE)
                .reduce((a, b) -> b).orElse(null);
        boolean engineManual = app.getStatus() == ApplicationStatus.ENGINE_FAILED_MANUAL;

        if (!engineManual && (current == null || !"REFER".equals(current.outcome()))) {
            throw new ApiProblem(HttpStatus.CONFLICT, "Conflict",
                    "Application " + publicId + " is not awaiting review");
        }

        String decision = request.decision();
        if (!"APPROVED".equals(decision) && !"DECLINED".equals(decision)) {
            throw unprocessable("Decision must be APPROVED or DECLINED");
        }
        OverrideCode code = parseCode(request.overrideCode());
        String note = request.note() == null ? "" : request.note().trim();
        if (note.length() < 10) {
            throw unprocessable("Add a note of at least 10 characters explaining what you verified");
        }

        Integer atpMax = base == null ? null : base.atpMax();
        int creditLimit;
        if ("APPROVED".equals(decision)) {
            Integer requested = request.creditLimit();
            if (requested == null || requested < 300) {
                throw unprocessable("Limit must be at least $300");
            }
            if (atpMax != null && requested > atpMax) {
                throw unprocessable("Limit exceeds the ability-to-pay maximum of " + Usd.format(atpMax));
            }
            creditLimit = requested;
        } else {
            creditLimit = 0; // DECLINED forces a zero limit
        }

        LedgerEntry entry = engineManual
                ? engineManualOverride(app, decision, creditLimit, code, note)
                : referOverride(base, app, decision, creditLimit, code, note);
        LedgerRecord row = ledgerWriter.append(entry);

        app.changeStatus(ApplicationStatus.REVIEWED);
        app.setUpdatedAt(Instant.now(clock).truncatedTo(ChronoUnit.MICROS));
        applicationRepository.save(app);

        return new ReviewViews.RecordResult(app.getPublicId(), row.seq(), decision, creditLimit);
    }

    /** OVERRIDE of a REFER decision: mirrors the base row, linked to it. */
    private LedgerEntry referOverride(LedgerRecord base, ApplicationEntity app, String decision,
                                      int creditLimit, OverrideCode code, String note) {
        return LedgerEntry.builder(LedgerKind.OVERRIDE, LedgerSource.LIVE)
                .application(app.getId(), app.getPublicId())
                .ruleVersion(base.ruleVersion())
                .scorecardVersion(base.scorecardVersion())
                .engineVersion(base.engineVersion())
                .bureau(base.bureauPullId(), base.bureauReused())
                .engineInput(base.engineInput())
                .outcome(decision)
                .creditLimit(creditLimit)
                .reasonCodes(base.reasonCodes())
                .fraudFlags(base.fraudFlags())
                .atpMax(base.atpMax())
                .linkedSeq(base.seq())
                .overrideDetail(detail(code, note, base.seq()))
                .build();
    }

    /**
     * OVERRIDE for an ENGINE_FAILED_MANUAL application, which has no base row: rebuild the EngineInput
     * from the stored application and its reused bureau pull, stamp the LIVE rule version (SPEC §6).
     */
    private LedgerEntry engineManualOverride(ApplicationEntity app, String decision, int creditLimit,
                                             OverrideCode code, String note) {
        ApplicationRequest form = ApplicationForms.formOf(app);
        BureauReport report = bureauService.pullFor(app, form); // reuses the stored pull
        DerivedFeatures features = featureService.derive(app, form, report);
        EngineInput input = engineInputMapper.toEngineInput(app, report, features);
        LiveRule live = liveRuleService.current();
        return LedgerEntry.builder(LedgerKind.OVERRIDE, LedgerSource.LIVE)
                .application(app.getId(), app.getPublicId())
                .ruleVersion(live.version())
                .bureau(report.pullId(), report.reused())
                .engineInput(input)
                .outcome(decision)
                .creditLimit(creditLimit)
                .reasonCodes(List.of())
                .fraudFlags(List.of())
                .linkedSeq(null)
                .overrideDetail(detail(code, note, null))
                .build();
    }

    private Map<String, Object> detail(OverrideCode code, String note, Long linkedSeq) {
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("by", currentUser.displayName());
        detail.put("username", currentUser.username());
        detail.put("code", code.name());
        detail.put("codeDescription", code.description());
        detail.put("note", note);
        detail.put("linkedSeq", linkedSeq);
        return detail;
    }

    // --- override stats ---------------------------------------------------------------------------

    @Transactional(readOnly = true)
    public ReviewViews.OverrideStats overrideStats() {
        long[] refers = jdbc.queryForObject(REFERS_SQL, ReviewService::bands);
        long[] overridden = jdbc.queryForObject(OVERRIDDEN_SQL, ReviewService::bands);
        List<ReviewViews.Band> bands = List.of(
                band("<620", refers[0], overridden[0]),
                band("620–679", refers[1], overridden[1]),
                band("680+", refers[2], overridden[2]),
                band("no score", refers[3], overridden[3]));
        return new ReviewViews.OverrideStats(bands);
    }

    private static long[] bands(java.sql.ResultSet rs, int rowNum) throws java.sql.SQLException {
        return new long[]{rs.getLong("lt620"), rs.getLong("mid"), rs.getLong("hi"), rs.getLong("none")};
    }

    private static ReviewViews.Band band(String name, long refers, long overridden) {
        double rate = refers == 0 ? 0.0
                : BigDecimal.valueOf(overridden).divide(BigDecimal.valueOf(refers), 4, RoundingMode.HALF_UP)
                        .doubleValue();
        return new ReviewViews.Band(name, refers, overridden, rate);
    }

    // --- helpers ----------------------------------------------------------------------------------

    private OverrideCode parseCode(String code) {
        try {
            return OverrideCode.valueOf(code);
        } catch (IllegalArgumentException | NullPointerException e) {
            throw unprocessable("Unknown override code: " + code);
        }
    }

    private ApiProblem unprocessable(String detail) {
        return new ApiProblem(HttpStatus.UNPROCESSABLE_ENTITY, "Unprocessable Entity", detail);
    }

    private String displayName(String role, byte[] nameEnc, String nameMasked) {
        return displayNamePolicy.fullName(role) ? dataCipher.decrypt(nameEnc) : nameMasked;
    }

    @SuppressWarnings("unchecked")
    private List<String> parseList(String json) {
        return json == null ? List.of() : canonicalJson.read(json, List.class);
    }
}
