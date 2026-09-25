package com.parallax.application.ledger;

import com.parallax.application.json.CanonicalJson;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Verifies the ledger hash chain (SPEC §5): each row's prev_hash must equal the previous row's hash
 * (genesis = 64 zeros for the first), and each row's hash must recompute from its stored fields.
 * {@link #verify()} streams rows ordered by seq; {@link #verify(List)} checks an in-memory copy.
 */
@Component
public class LedgerVerifier {

    private static final String GENESIS_PREV_HASH = "0".repeat(64);

    private final JdbcTemplate jdbcTemplate;
    private final LedgerReader ledgerReader;
    private final LedgerCanonicalizer canonicalizer;
    private final CanonicalJson canonicalJson;

    public LedgerVerifier(JdbcTemplate jdbcTemplate, LedgerReader ledgerReader,
                          LedgerCanonicalizer canonicalizer, CanonicalJson canonicalJson) {
        this.jdbcTemplate = jdbcTemplate;
        this.ledgerReader = ledgerReader;
        this.canonicalizer = canonicalizer;
        this.canonicalJson = canonicalJson;
    }

    @Transactional(readOnly = true)
    public VerifyResult verify() {
        long start = System.nanoTime();
        RowMapper<LedgerRecord> mapper = ledgerReader.rowMapper();
        Chain chain = new Chain();
        jdbcTemplate.query(connection -> {
            var ps = connection.prepareStatement(LedgerReader.SELECT + " ORDER BY dl.seq");
            ps.setFetchSize(5000);
            return ps;
        }, (ResultSetExtractor<Void>) rs -> {
            int rowNum = 0;
            while (rs.next()) {
                if (!chain.advance(mapper.mapRow(rs, rowNum++))) {
                    break;
                }
            }
            return null;
        });
        return chain.result((System.nanoTime() - start) / 1_000_000);
    }

    public VerifyResult verify(List<LedgerRecord> records) {
        long start = System.nanoTime();
        Chain chain = new Chain();
        for (LedgerRecord record : records) {
            if (!chain.advance(record)) {
                break;
            }
        }
        return chain.result((System.nanoTime() - start) / 1_000_000);
    }

    /** Walks the chain, stopping at the first broken row. */
    private final class Chain {
        private String expectedPrev = GENESIS_PREV_HASH;
        private int checked;
        private Long brokenAtSeq;
        private boolean ok = true;

        boolean advance(LedgerRecord record) {
            if (!record.prevHash().equals(expectedPrev)) {
                return fail(record);
            }
            String recomputed = canonicalJson.sha256Hex(record.prevHash() + "|" + canonicalizer.payload(record));
            if (!recomputed.equals(record.hash())) {
                return fail(record);
            }
            checked++;
            expectedPrev = record.hash();
            return true;
        }

        private boolean fail(LedgerRecord record) {
            ok = false;
            brokenAtSeq = record.seq();
            return false;
        }

        VerifyResult result(long ms) {
            return new VerifyResult(ok, checked, brokenAtSeq, ms);
        }
    }
}
