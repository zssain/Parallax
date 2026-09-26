package com.parallax.application.lab;

import com.parallax.application.json.CanonicalJson;
import com.parallax.engine.model.EngineInput;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import javax.sql.DataSource;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

/**
 * Streams replayable ledger rows (SPEC §10) with keyset pagination, 10,000 per page, fetch size 5,000,
 * inside a read-only transaction. Only DECISION/REDECISION rows with a full EngineInput (bureau not
 * UNAVAILABLE) are returned; each row carries its recorded outcome and known loan outcome (if any).
 */
@Component
public class ReplayLoader {

    static final int PAGE_SIZE = 10_000;

    private static final String COUNT_FROM = """
            FROM decision_ledger l
            JOIN application a ON a.id = l.application_id
            WHERE l.kind IN ('DECISION','REDECISION')
              AND l.engine_input->>'bureau' IS DISTINCT FROM 'UNAVAILABLE'
            """;

    private static final String PAGE_FROM = """
            FROM decision_ledger l
            JOIN application a ON a.id = l.application_id
            LEFT JOIN loan_outcome o ON o.application_id = l.application_id
            WHERE l.kind IN ('DECISION','REDECISION')
              AND l.engine_input->>'bureau' IS DISTINCT FROM 'UNAVAILABLE'
            """;

    private final JdbcTemplate jdbc;
    private final CanonicalJson canonicalJson;

    public ReplayLoader(DataSource dataSource, CanonicalJson canonicalJson) {
        this.jdbc = new JdbcTemplate(dataSource);
        this.jdbc.setFetchSize(5000);
        this.canonicalJson = canonicalJson;
    }

    /** Total replayable rows for the optional created_at window (used for progress). */
    @Transactional(readOnly = true)
    public long total(Instant from, Instant to) {
        List<Object> args = new ArrayList<>();
        String sql = "SELECT count(*) " + COUNT_FROM + windowClause(from, to, args);
        Long total = jdbc.queryForObject(sql, Long.class, args.toArray());
        return total == null ? 0 : total;
    }

    /** One keyset page of rows with seq &gt; afterSeq, ordered by seq. */
    @Transactional(readOnly = true)
    public List<ReplayRow> page(long afterSeq, Instant from, Instant to) {
        List<Object> args = new ArrayList<>();
        args.add(afterSeq);
        String sql = "SELECT l.seq, l.engine_input, l.outcome AS recorded_outcome, a.public_id,"
                + " o.defaulted, o.simulated "
                + PAGE_FROM
                + " AND l.seq > ?"
                + windowClause(from, to, args)
                + " ORDER BY l.seq LIMIT " + PAGE_SIZE;
        return jdbc.query(sql, rowMapper(), args.toArray());
    }

    private String windowClause(Instant from, Instant to, List<Object> args) {
        StringBuilder sql = new StringBuilder();
        if (from != null) {
            sql.append(" AND l.created_at >= ?");
            args.add(from.atOffset(ZoneOffset.UTC));
        }
        if (to != null) {
            sql.append(" AND l.created_at <= ?");
            args.add(to.atOffset(ZoneOffset.UTC));
        }
        return sql.toString();
    }

    private RowMapper<ReplayRow> rowMapper() {
        return (rs, i) -> new ReplayRow(
                rs.getLong("seq"),
                canonicalJson.read(rs.getString("engine_input"), EngineInput.class),
                rs.getString("recorded_outcome"),
                rs.getString("public_id"),
                rs.getObject("defaulted", Boolean.class),
                rs.getObject("simulated", Boolean.class));
    }
}
