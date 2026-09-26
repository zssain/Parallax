package db.migration;

import com.parallax.application.json.CanonicalJson;
import com.parallax.application.ledger.LedgerCanonicalizer;
import com.parallax.application.ledger.LedgerKind;
import com.parallax.application.ledger.LedgerRecord;
import com.parallax.application.ledger.LedgerSource;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Records the historical promotion of v1.3 to LIVE as the genesis GOVERNANCE ledger row (SPEC §10, §14).
 * Runs before any decision so it becomes seq 1 (prevHash = genesis) on a fresh database; the seeder then
 * chains its SEED decisions after it (HistorySeeder ignores GOVERNANCE rows). Idempotent: if a GOVERNANCE
 * row already exists it does nothing. The hash is computed with the same {@link LedgerCanonicalizer} the
 * runtime uses, so {@code GET /api/v1/ledger/verify} accepts the chain.
 *
 * <p>Also grants parallax_app DELETE on replay_job — the Prompt 14 version-delete endpoint removes a
 * candidate's replay_job rows, and V4 (Prompt 13) granted only SELECT/INSERT/UPDATE there.
 */
public class V5__genesis_governance extends BaseJavaMigration {

    private static final String GENESIS_PREV_HASH = "0".repeat(64);

    private static final String INSERT = """
            INSERT INTO decision_ledger
                (seq, kind, source, application_id, rule_version, scorecard_version, engine_version,
                 bureau_pull_id, bureau_reused, engine_input, outcome, score, credit_limit, reason_codes,
                 fraud_flags, atp_max, linked_seq, override_detail, governance_detail, created_at,
                 prev_hash, hash)
            VALUES (?, 'GOVERNANCE', 'SEED', NULL, 'v1.3', NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL,
                    NULL, NULL, NULL, NULL, NULL, ?::jsonb, ?, ?, ?)
            """;

    @Override
    public void migrate(Context context) throws Exception {
        context.getConnection().createStatement().execute("GRANT DELETE ON replay_job TO parallax_app");

        if (hasGovernanceRow(context)) {
            return;
        }

        long seq = 1;
        String prevHash = GENESIS_PREV_HASH;
        try (PreparedStatement ps = context.getConnection().prepareStatement(
                "SELECT seq, hash FROM decision_ledger ORDER BY seq DESC LIMIT 1");
             ResultSet rs = ps.executeQuery()) {
            if (rs.next()) {
                seq = rs.getLong("seq") + 1;
                prevHash = rs.getString("hash").trim();
            }
        }

        Instant createdAt = OffsetDateTime.of(2026, 8, 14, 16, 2, 0, 0, ZoneOffset.UTC).toInstant();

        Map<String, Object> governance = new LinkedHashMap<>();
        governance.put("action", "PROMOTE");
        governance.put("version", "v1.3");
        governance.put("replaced", "v1.2");
        governance.put("proposedBy", "Aditi Rao");
        governance.put("approvedBy", "Vikram Nair");
        governance.put("replayJobId", null);
        governance.put("note", "v1.3 promoted to LIVE · proposed by Aditi Rao · approved by Vikram Nair");

        CanonicalJson canonicalJson = new CanonicalJson();
        LedgerCanonicalizer canonicalizer = new LedgerCanonicalizer(canonicalJson);
        LedgerRecord draft = new LedgerRecord(seq, LedgerKind.GOVERNANCE, LedgerSource.SEED,
                null, null, "v1.3", null, null, null, null, null, null, null, null, null, null,
                null, null, null, governance, createdAt, prevHash, null);
        String hash = canonicalJson.sha256Hex(prevHash + "|" + canonicalizer.payload(draft));

        try (PreparedStatement ps = context.getConnection().prepareStatement(INSERT)) {
            ps.setLong(1, seq);
            ps.setString(2, canonicalJson.write(governance));
            ps.setObject(3, createdAt.atOffset(ZoneOffset.UTC));
            ps.setString(4, prevHash);
            ps.setString(5, hash);
            ps.executeUpdate();
        }
    }

    private boolean hasGovernanceRow(Context context) throws Exception {
        try (PreparedStatement ps = context.getConnection().prepareStatement(
                "SELECT count(*) FROM decision_ledger WHERE kind = 'GOVERNANCE'");
             ResultSet rs = ps.executeQuery()) {
            rs.next();
            return rs.getLong(1) > 0;
        }
    }
}
