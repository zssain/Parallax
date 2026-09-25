package db.migration;

import com.parallax.application.json.CanonicalJson;
import com.parallax.engine.config.RuleConfigs;
import com.parallax.engine.model.RuleConfig;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

import java.sql.PreparedStatement;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

/**
 * Seeds the two rule versions from SPEC §4. The config JSON and its config_hash are computed here
 * with {@link CanonicalJson} from the typed {@link RuleConfig} objects — a hash is never hand-typed.
 * v1.2 is RETIRED, v1.3 is the single LIVE version.
 */
public class V2__seed_rule_versions extends BaseJavaMigration {

    private static final String INSERT = """
            INSERT INTO rule_version
                (version, status, config, config_hash, note, created_by, created_at,
                 proposed_by, approved_by, promoted_at, retired_at, first_used_at, rejection_note)
            VALUES (?, ?, ?::jsonb, ?, ?, ?, ?, ?, ?, ?, ?, NULL, NULL)
            """;

    @Override
    public void migrate(Context context) throws Exception {
        CanonicalJson canonicalJson = new CanonicalJson();

        // v1.2 — RETIRED. Created 2026-06-02, promoted then, retired 2026-08-14 when v1.3 went live.
        insert(context, canonicalJson, "v1.2", "RETIRED", RuleConfigs.v1_2(),
                "Initial production scorecard.",
                LocalDate.of(2026, 6, 2), LocalDate.of(2026, 6, 2), LocalDate.of(2026, 8, 14));

        // v1.3 — LIVE. Created and promoted 2026-08-14; not yet retired.
        insert(context, canonicalJson, "v1.3", "LIVE", RuleConfigs.v1_3(),
                "Tightened utilization bands after Q2 review.",
                LocalDate.of(2026, 8, 14), LocalDate.of(2026, 8, 14), null);
    }

    private void insert(Context context, CanonicalJson canonicalJson, String version, String status,
                        RuleConfig config, String note,
                        LocalDate createdAt, LocalDate promotedAt, LocalDate retiredAt) throws Exception {
        String json = canonicalJson.write(config);
        String hash = canonicalJson.sha256Hex(json);
        try (PreparedStatement ps = context.getConnection().prepareStatement(INSERT)) {
            ps.setString(1, version);
            ps.setString(2, status);
            ps.setString(3, json);
            ps.setString(4, hash);
            ps.setString(5, note);
            ps.setString(6, "Aditi Rao");            // created_by
            ps.setObject(7, atUtc(createdAt));        // created_at
            ps.setString(8, "Aditi Rao");            // proposed_by (maker)
            ps.setString(9, "Vikram Nair");          // approved_by (checker)
            ps.setObject(10, atUtc(promotedAt));      // promoted_at
            ps.setObject(11, retiredAt == null ? null : atUtc(retiredAt)); // retired_at
            ps.executeUpdate();
        }
    }

    private static OffsetDateTime atUtc(LocalDate date) {
        return date.atStartOfDay().atOffset(ZoneOffset.UTC);
    }
}
