package com.parallax.application.query;

import com.parallax.application.json.CanonicalJson;
import com.parallax.application.ledger.LedgerReader;
import com.parallax.application.ledger.LedgerRecord;
import com.parallax.application.ledger.LedgerVerifier;
import com.parallax.application.ledger.VerifyResult;
import com.parallax.application.pii.DataCipher;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Read model and demo helpers for the Decision-ledger screen (SPEC §15). */
@Service
public class LedgerQueryService {

    private static final String UPDATE_DEMO_SQL =
            "UPDATE decision_ledger SET outcome='APPROVED' WHERE seq = (SELECT max(seq) FROM decision_ledger)";

    private final JdbcTemplate jdbc;
    private final LedgerReader ledgerReader;
    private final LedgerVerifier ledgerVerifier;
    private final DataCipher dataCipher;
    private final DisplayNamePolicy displayNamePolicy;
    private final CanonicalJson canonicalJson;

    public LedgerQueryService(JdbcTemplate jdbc, LedgerReader ledgerReader, LedgerVerifier ledgerVerifier,
                              DataCipher dataCipher, DisplayNamePolicy displayNamePolicy,
                              CanonicalJson canonicalJson) {
        this.jdbc = jdbc;
        this.ledgerReader = ledgerReader;
        this.ledgerVerifier = ledgerVerifier;
        this.dataCipher = dataCipher;
        this.displayNamePolicy = displayNamePolicy;
        this.canonicalJson = canonicalJson;
    }

    @Transactional(readOnly = true)
    public LedgerViews.ListResponse list(String role, String source, int page, int size) {
        String effectiveSource = (source == null || source.isBlank()) ? "ALL" : source.toUpperCase();
        boolean allSources = "ALL".equals(effectiveSource);

        String where = allSources ? "" : " WHERE dl.source = ?";
        Object[] countArgs = allSources ? new Object[0] : new Object[]{effectiveSource};
        long total = Objects.requireNonNullElse(jdbc.queryForObject(
                "SELECT count(*) FROM decision_ledger dl" + where, Long.class, countArgs), 0L);

        String sql = """
                SELECT dl.seq, dl.kind, dl.source, a.public_id, a.name_enc, a.name_masked, dl.outcome,
                       dl.rule_version, dl.created_at, dl.prev_hash, dl.hash, dl.governance_detail
                FROM decision_ledger dl LEFT JOIN application a ON a.id = dl.application_id
                """ + where + " ORDER BY dl.seq DESC LIMIT ? OFFSET ?";
        List<Object> args = new ArrayList<>();
        if (!allSources) {
            args.add(effectiveSource);
        }
        args.add(size);
        args.add((long) page * size);

        List<LedgerViews.ListItem> items = jdbc.query(sql, (rs, i) -> new LedgerViews.ListItem(
                rs.getLong("seq"),
                rs.getString("kind"),
                rs.getString("source"),
                rs.getString("public_id"),
                displayName(role, rs.getString("public_id"), rs.getBytes("name_enc"), rs.getString("name_masked")),
                note(rs.getString("kind"), rs.getString("governance_detail")),
                rs.getString("outcome"),
                rs.getString("rule_version"),
                rs.getObject("created_at", OffsetDateTime.class).toInstant(),
                rs.getString("prev_hash").trim(),
                rs.getString("hash").trim()), args.toArray());

        return new LedgerViews.ListResponse(items, page, size, total);
    }

    @Transactional(readOnly = true)
    public LedgerViews.Stats stats() {
        long records = countKind(null);
        long decisions = countKind("DECISION") + countKind("REDECISION");
        long overrides = countKind("OVERRIDE");
        long governance = countKind("GOVERNANCE");
        return new LedgerViews.Stats(records, decisions, overrides, governance);
    }

    public VerifyResult verify() {
        return ledgerVerifier.verify();
    }

    /** Dev demo: prove parallax_app cannot UPDATE the ledger; return the database error. */
    public LedgerViews.DemoUpdate attemptUpdate() {
        try {
            jdbc.update(UPDATE_DEMO_SQL);
            return new LedgerViews.DemoUpdate(UPDATE_DEMO_SQL, "unexpectedly succeeded");
        } catch (DataAccessException e) {
            return new LedgerViews.DemoUpdate(UPDATE_DEMO_SQL, rootMessage(e));
        }
    }

    /** Dev demo: tamper an in-memory copy of the middle row and prove verify() catches it. */
    @Transactional(readOnly = true)
    public LedgerViews.TamperSimulation tamperSimulation() {
        List<LedgerRecord> rows = new ArrayList<>(jdbc.query(
                LedgerReader.SELECT + " ORDER BY dl.seq DESC LIMIT 50000", ledgerReader.rowMapper()));
        rows.sort((a, b) -> Long.compare(a.seq(), b.seq()));
        if (rows.isEmpty()) {
            return new LedgerViews.TamperSimulation(0, null, 0);
        }
        int mid = rows.size() / 2;
        LedgerRecord original = rows.get(mid);
        rows.set(mid, withTamper(original));
        VerifyResult result = ledgerVerifier.verify(rows);
        return new LedgerViews.TamperSimulation(original.seq(), result.brokenAtSeq(), result.checked());
    }

    private LedgerRecord withTamper(LedgerRecord r) {
        return new LedgerRecord(r.seq(), r.kind(), r.source(), r.applicationDbId(), r.applicationPublicId(),
                r.ruleVersion(), r.scorecardVersion(), r.engineVersion(), r.bureauPullId(), r.bureauReused(),
                r.engineInput(), "APPROVED", r.score(), 25000, r.reasonCodes(), r.fraudFlags(), r.atpMax(),
                r.linkedSeq(), r.overrideDetail(), r.governanceDetail(), r.createdAt(), r.prevHash(), r.hash());
    }

    private long countKind(String kind) {
        if (kind == null) {
            return Objects.requireNonNullElse(
                    jdbc.queryForObject("SELECT count(*) FROM decision_ledger", Long.class), 0L);
        }
        return Objects.requireNonNullElse(
                jdbc.queryForObject("SELECT count(*) FROM decision_ledger WHERE kind = ?", Long.class, kind), 0L);
    }

    private String displayName(String role, String publicId, byte[] nameEnc, String nameMasked) {
        if (publicId == null) {
            return null; // governance rows have no application
        }
        return displayNamePolicy.fullName(role) ? dataCipher.decrypt(nameEnc) : nameMasked;
    }

    private String note(String kind, String governanceDetail) {
        if (!"GOVERNANCE".equals(kind) || governanceDetail == null) {
            return null;
        }
        Map<?, ?> detail = canonicalJson.read(governanceDetail, Map.class);
        Object note = detail.get("note");
        return note == null ? null : note.toString();
    }

    private static String rootMessage(Throwable e) {
        Throwable cause = e;
        while (cause != null) {
            if (cause instanceof SQLException sql) {
                return sql.getMessage();
            }
            cause = cause.getCause();
        }
        return e.getMessage();
    }
}
