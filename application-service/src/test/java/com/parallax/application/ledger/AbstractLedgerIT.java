package com.parallax.application.ledger;

import com.parallax.application.AbstractPostgresIT;
import com.parallax.application.json.CanonicalJson;
import com.parallax.engine.model.EngineInput;
import com.parallax.engine.model.PullType;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;

/**
 * Base for ledger integration tests: a transaction template to satisfy the writer's MANDATORY
 * propagation, an application-row fixture (the ledger FK), and a parallax_owner JdbcTemplate for the
 * privileged truncate/tamper operations that parallax_app is denied.
 */
public abstract class AbstractLedgerIT extends AbstractPostgresIT {

    @Autowired
    protected JdbcTemplate jdbc; // parallax_app (runtime role)

    @Autowired
    protected LedgerWriter ledgerWriter;

    @Autowired
    protected LedgerVerifier ledgerVerifier;

    @Autowired
    protected LedgerReader ledgerReader;

    @Autowired
    protected LedgerCanonicalizer ledgerCanonicalizer;

    @Autowired
    protected CanonicalJson canonicalJson;

    @Autowired
    protected PlatformTransactionManager transactionManager;

    protected TransactionTemplate tx;
    protected JdbcTemplate ownerJdbc;

    @BeforeEach
    void ledgerSetup() {
        tx = new TransactionTemplate(transactionManager);
        String url = "jdbc:postgresql://" + POSTGRES.getHost() + ":" + POSTGRES.getMappedPort(5432) + "/parallax";
        ownerJdbc = new JdbcTemplate(new DriverManagerDataSource(url, "parallax_owner", "owner-dev"));
        ownerJdbc.execute("TRUNCATE decision_ledger, application RESTART IDENTITY CASCADE");
    }

    /** Append inside a transaction (LedgerWriter.append is MANDATORY). */
    protected LedgerRecord appendInTx(LedgerEntry entry) {
        return tx.execute(status -> ledgerWriter.append(entry));
    }

    /** Recompute a row's hash the same way the verifier does. */
    protected String recomputeHash(LedgerRecord record) {
        return canonicalJson.sha256Hex(record.prevHash() + "|" + ledgerCanonicalizer.payload(record));
    }

    protected List<LedgerRecord> readAll() {
        return jdbc.query(LedgerReader.SELECT + " ORDER BY dl.seq", ledgerReader.rowMapper());
    }

    /** A simple APPROVED DECISION entry (Ishaan-like) for chain/concurrency tests. */
    protected LedgerEntry decisionEntry(long applicationDbId, String publicId) {
        EngineInput input = new EngineInput(30, 1996, 64000, 1350, 280, 0.08, 0, 0, 12, 156,
                true, true, false, 1997, false, 1, PullType.HARD);
        return LedgerEntry.builder(LedgerKind.DECISION, LedgerSource.LIVE)
                .application(applicationDbId, publicId)
                .ruleVersion("v1.3").scorecardVersion("sc-2.1").engineVersion("engine-1.0.0")
                .bureau("BP-TEST0001", false)
                .engineInput(input)
                .outcome("APPROVED").score(830).creditLimit(12000)
                .reasonCodes(List.of()).fraudFlags(List.of()).atpMax(29200)
                .build();
    }

    /** Insert a minimal parent application row and return its generated id. */
    protected long insertApplication(String publicId) {
        Instant now = Instant.now();
        return jdbc.queryForObject("""
                INSERT INTO application
                    (public_id, client_id, name_enc, name_masked, ssn_enc, ssn_token, ssn_last4, dob_enc,
                     birth_year, address_enc, annual_income, monthly_housing, monthly_debt,
                     independent_income, bureau_consent, product, status, source, engine_attempts,
                     created_at, updated_at)
                VALUES (?, 'tester', ?, 'T••••', ?, ?, '1234', ?, 1990, ?, 50000, 1000, 200,
                        true, true, 'REWARDS_CARD', 'RECEIVED', 'LIVE', 0, ?, ?)
                RETURNING id
                """, Long.class,
                publicId, new byte[]{1}, new byte[]{1}, "0".repeat(64), new byte[]{1}, new byte[]{1},
                Timestamp.from(now), Timestamp.from(now));
    }
}
