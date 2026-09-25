package com.parallax.application.ledger;

import com.parallax.application.json.CanonicalJson;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Appends a row to the hash-chained decision ledger (SPEC §5). Runs only inside the caller's
 * transaction ({@code MANDATORY}) so the decision and its ledger row commit together. Rows are
 * serialized with {@code pg_advisory_xact_lock} so seq and prevHash are assigned without races.
 */
@Component
public class LedgerWriter {

    /** Advisory lock key that serializes ledger inserts within a transaction (SPEC §5). */
    private static final long LEDGER_LOCK_KEY = 727274L;
    private static final String GENESIS_PREV_HASH = "0".repeat(64);

    private static final String INSERT = """
            INSERT INTO decision_ledger
                (seq, kind, source, application_id, rule_version, scorecard_version, engine_version,
                 bureau_pull_id, bureau_reused, engine_input, outcome, score, credit_limit, reason_codes,
                 fraud_flags, atp_max, linked_seq, override_detail, governance_detail, created_at,
                 prev_hash, hash)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?, ?, ?, ?::jsonb, ?::jsonb, ?, ?, ?::jsonb, ?::jsonb, ?, ?, ?)
            """;

    private final JdbcTemplate jdbcTemplate;
    private final LedgerCanonicalizer canonicalizer;
    private final CanonicalJson canonicalJson;
    private final Clock clock;

    public LedgerWriter(JdbcTemplate jdbcTemplate, LedgerCanonicalizer canonicalizer,
                        CanonicalJson canonicalJson, Clock clock) {
        this.jdbcTemplate = jdbcTemplate;
        this.canonicalizer = canonicalizer;
        this.canonicalJson = canonicalJson;
        this.clock = clock;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public LedgerRecord append(LedgerEntry entry) {
        jdbcTemplate.execute("SELECT pg_advisory_xact_lock(" + LEDGER_LOCK_KEY + ")");

        List<SeqHash> last = jdbcTemplate.query(
                "SELECT seq, hash FROM decision_ledger ORDER BY seq DESC LIMIT 1",
                (rs, i) -> new SeqHash(rs.getLong("seq"), rs.getString("hash").trim()));
        long seq = last.isEmpty() ? 1L : last.get(0).seq() + 1;
        String prevHash = last.isEmpty() ? GENESIS_PREV_HASH : last.get(0).hash();

        Instant createdAt = Instant.now(clock).truncatedTo(ChronoUnit.MICROS);

        LedgerRecord draft = toRecord(entry, seq, createdAt, prevHash, null);
        String hash = canonicalJson.sha256Hex(prevHash + "|" + canonicalizer.payload(draft));
        LedgerRecord record = toRecord(entry, seq, createdAt, prevHash, hash);

        jdbcTemplate.update(INSERT,
                record.seq(),
                record.kind().name(),
                record.source().name(),
                record.applicationDbId(),
                record.ruleVersion(),
                record.scorecardVersion(),
                record.engineVersion(),
                record.bureauPullId(),
                record.bureauReused(),
                json(record.engineInput()),
                record.outcome(),
                record.score(),
                record.creditLimit(),
                json(record.reasonCodes()),
                json(record.fraudFlags()),
                record.atpMax(),
                record.linkedSeq(),
                json(record.overrideDetail()),
                json(record.governanceDetail()),
                createdAt.atOffset(ZoneOffset.UTC),
                record.prevHash(),
                record.hash());
        return record;
    }

    private LedgerRecord toRecord(LedgerEntry e, long seq, Instant createdAt, String prevHash, String hash) {
        return new LedgerRecord(seq, e.kind(), e.source(), e.applicationDbId(), e.applicationPublicId(),
                e.ruleVersion(), e.scorecardVersion(), e.engineVersion(), e.bureauPullId(), e.bureauReused(),
                e.engineInput(), e.outcome(), e.score(), e.creditLimit(), e.reasonCodes(), e.fraudFlags(),
                e.atpMax(), e.linkedSeq(), e.overrideDetail(), e.governanceDetail(), createdAt, prevHash, hash);
    }

    private String json(Object value) {
        return value == null ? null : canonicalJson.write(value);
    }

    private record SeqHash(long seq, String hash) {
    }
}
