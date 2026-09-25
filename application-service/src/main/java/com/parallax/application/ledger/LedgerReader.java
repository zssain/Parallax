package com.parallax.application.ledger;

import com.parallax.application.json.CanonicalJson;
import com.parallax.engine.model.EngineInput;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

/**
 * Reads decision-ledger rows back into {@link LedgerRecord}s, parsing {@code engine_input} jsonb into
 * the typed record it was written from (SPEC §5) — a full {@link EngineInput}, or a
 * {@link PartialEngineInput} when the JSON carries a {@code bureau} field. The base SELECT joins the
 * application table so each row carries its public id for hashing.
 */
@Component
public class LedgerReader {

    /** Base projection; consumers append WHERE/ORDER BY. Always resolves application_public_id. */
    public static final String SELECT = """
            SELECT dl.seq, dl.kind, dl.source, dl.application_id, a.public_id AS application_public_id,
                   dl.rule_version, dl.scorecard_version, dl.engine_version, dl.bureau_pull_id,
                   dl.bureau_reused, dl.engine_input, dl.outcome, dl.score, dl.credit_limit,
                   dl.reason_codes, dl.fraud_flags, dl.atp_max, dl.linked_seq, dl.override_detail,
                   dl.governance_detail, dl.created_at, dl.prev_hash, dl.hash
            FROM decision_ledger dl LEFT JOIN application a ON a.id = dl.application_id
            """;

    private final CanonicalJson canonicalJson;

    public LedgerReader(CanonicalJson canonicalJson) {
        this.canonicalJson = canonicalJson;
    }

    public RowMapper<LedgerRecord> rowMapper() {
        return (rs, rowNum) -> new LedgerRecord(
                rs.getLong("seq"),
                LedgerKind.valueOf(rs.getString("kind")),
                LedgerSource.valueOf(rs.getString("source")),
                rs.getObject("application_id", Long.class),
                rs.getString("application_public_id"),
                rs.getString("rule_version"),
                rs.getString("scorecard_version"),
                rs.getString("engine_version"),
                rs.getString("bureau_pull_id"),
                rs.getObject("bureau_reused", Boolean.class),
                parseEngineInput(rs.getString("engine_input")),
                rs.getString("outcome"),
                rs.getObject("score", Integer.class),
                rs.getObject("credit_limit", Integer.class),
                parseStringList(rs.getString("reason_codes")),
                parseStringList(rs.getString("fraud_flags")),
                rs.getObject("atp_max", Integer.class),
                rs.getObject("linked_seq", Long.class),
                parseMap(rs.getString("override_detail")),
                parseMap(rs.getString("governance_detail")),
                rs.getObject("created_at", OffsetDateTime.class).toInstant(),
                trim(rs.getString("prev_hash")),
                trim(rs.getString("hash")));
    }

    private Object parseEngineInput(String json) {
        if (json == null) {
            return null;
        }
        Map<?, ?> peek = canonicalJson.read(json, Map.class);
        return peek.containsKey("bureau")
                ? canonicalJson.read(json, PartialEngineInput.class)
                : canonicalJson.read(json, EngineInput.class);
    }

    @SuppressWarnings("unchecked")
    private List<String> parseStringList(String json) {
        return json == null ? null : canonicalJson.read(json, List.class);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseMap(String json) {
        return json == null ? null : canonicalJson.read(json, Map.class);
    }

    private static String trim(String value) {
        return value == null ? null : value.trim();
    }
}
