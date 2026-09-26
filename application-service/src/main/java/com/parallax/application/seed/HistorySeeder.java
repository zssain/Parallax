package com.parallax.application.seed;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.parallax.application.domain.ApplicationEntity;
import com.parallax.application.domain.ApplicationRepository;
import com.parallax.application.domain.ApplicationStatus;
import com.parallax.application.ledger.LedgerEntry;
import com.parallax.application.ledger.LedgerKind;
import com.parallax.application.ledger.LedgerSource;
import com.parallax.application.ledger.LedgerVerifier;
import com.parallax.application.ledger.LedgerWriter;
import com.parallax.application.ledger.VerifyResult;
import com.parallax.application.pii.NameMasker;
import com.parallax.application.pii.Tokenizer;
import com.parallax.engine.config.RuleConfigs;
import com.parallax.engine.model.Decision;
import com.parallax.engine.model.EngineInput;
import com.parallax.engine.model.EngineVersion;
import com.parallax.engine.model.Outcome;
import com.parallax.engine.model.ReasonCode;
import com.parallax.engine.model.RuleConfig;
import com.parallax.engine.model.ScorecardVersion;
import com.parallax.engine.scoring.DecisionEngine;
import com.parallax.generator.GeneratedApplicant;
import com.parallax.generator.HistoryGenerator;
import com.parallax.generator.HistoryGenerator.HistoryParams;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.stream.Stream;

/**
 * Ingests synthetic history into the ledger as SEED rows with a known {@code loan_outcome} (SPEC §12,
 * Prompt 12). Records come from {@code parallax.seed.history-file} or, when a generate count is set,
 * from the in-process {@link HistoryGenerator} (defaults, as-of = today UTC). Refuses to run unless
 * the ledger holds nothing but GOVERNANCE rows; never deletes anything.
 */
@Component
public class HistorySeeder {

    static final String REFUSED_MESSAGE = "Seed refused: the ledger is not empty. To reseed, stop the "
            + "stack and delete the Postgres volume (docker compose down -v).";

    private static final Logger log = LoggerFactory.getLogger(HistorySeeder.class);
    private static final int BATCH_SIZE = 1000;
    private static final int MAX_RECORDS = 1_000_000;
    private static final long GENERATE_SEED = 20260925L;
    private static final String LOAN_OUTCOME_INSERT =
            "INSERT INTO loan_outcome (application_id, defaulted, months_observed, simulated) VALUES (?, ?, 12, ?)";
    private static final String[] PRODUCTS = {"REWARDS_CARD", "STORE_CARD", "HEALTHCARE_CARD"};

    private final JdbcTemplate jdbc;
    private final ApplicationRepository applicationRepository;
    private final LedgerWriter ledgerWriter;
    private final LedgerVerifier ledgerVerifier;
    private final Tokenizer tokenizer;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate txTemplate;

    private final String historyFile;
    private final int generateCount;

    public HistorySeeder(JdbcTemplate jdbc, ApplicationRepository applicationRepository,
                         LedgerWriter ledgerWriter, LedgerVerifier ledgerVerifier, Tokenizer tokenizer,
                         ObjectMapper objectMapper, PlatformTransactionManager txManager,
                         @Value("${parallax.seed.history-file:}") String historyFile,
                         @Value("${parallax.seed.generate-count:0}") int generateCount) {
        this.jdbc = jdbc;
        this.applicationRepository = applicationRepository;
        this.ledgerWriter = ledgerWriter;
        this.ledgerVerifier = ledgerVerifier;
        this.tokenizer = tokenizer;
        this.objectMapper = objectMapper;
        this.txTemplate = new TransactionTemplate(txManager);
        this.historyFile = historyFile;
        this.generateCount = generateCount;
    }

    /** The outcome of a seed run: refused with a message, or the counts and chain verification. */
    public record SeedSummary(boolean refused, String message, long count, long approved, long defaults,
                              VerifyResult verify) {
    }

    public SeedSummary seed() {
        Integer nonGovernance = jdbc.queryForObject(
                "SELECT count(*) FROM decision_ledger WHERE kind <> 'GOVERNANCE'", Integer.class);
        if (nonGovernance != null && nonGovernance > 0) {
            log.warn(REFUSED_MESSAGE);
            return new SeedSummary(true, REFUSED_MESSAGE, 0, 0, 0, null);
        }

        RuleConfig config = RuleConfigs.v1_3();
        Totals totals = new Totals();
        long processed = 0;

        try (Stream<SeedRecord> source = source()) {
            Iterator<SeedRecord> it = source.iterator();
            List<SeedRecord> chunk = new ArrayList<>(BATCH_SIZE);
            while (it.hasNext()) {
                chunk.add(it.next());
                if (chunk.size() >= BATCH_SIZE) {
                    processed += ingestChunk(chunk, config, totals);
                    chunk.clear();
                    logProgress(processed);
                }
            }
            if (!chunk.isEmpty()) {
                processed += ingestChunk(chunk, config, totals);
                logProgress(processed);
            }
        }

        // v1.3 is now in use: stamp first_used_at (immutable thereafter) with the earliest seed instant.
        jdbc.update("UPDATE rule_version SET first_used_at = "
                + "(SELECT min(created_at) FROM decision_ledger WHERE rule_version = 'v1.3') "
                + "WHERE version = 'v1.3' AND first_used_at IS NULL");

        VerifyResult verify = ledgerVerifier.verify();
        log.info("Seed complete: {} applications ({} approved, {} defaults). Ledger verify ok={} ({} rows).",
                processed, totals.approved, totals.defaults, verify.ok(), verify.checked());
        return new SeedSummary(false, null, processed, totals.approved, totals.defaults, verify);
    }

    private Stream<SeedRecord> source() {
        if (historyFile != null && !historyFile.isBlank()) {
            return fileRecords(historyFile);
        }
        HistoryParams params = new HistoryParams(generateCount, GENERATE_SEED, 365, 0.35, 60,
                LocalDate.now(ZoneOffset.UTC));
        return new HistoryGenerator().generate(params).map(HistorySeeder::toSeedRecord);
    }

    private static SeedRecord toSeedRecord(HistoryGenerator.HistoryRecord record) {
        GeneratedApplicant applicant = record.applicant();
        return new SeedRecord(record.i(), record.createdAt(), applicant.input(), applicant.defaulted());
    }

    private Stream<SeedRecord> fileRecords(String path) {
        try {
            return java.nio.file.Files.lines(java.nio.file.Path.of(path)).map(this::parseLine);
        } catch (java.io.IOException e) {
            throw new IllegalStateException("Cannot read history file " + path + ": " + e.getMessage(), e);
        }
    }

    private SeedRecord parseLine(String line) {
        try {
            JsonNode node = objectMapper.readTree(line);
            return new SeedRecord(node.get("i").asInt(), Instant.parse(node.get("createdAt").asText()),
                    objectMapper.treeToValue(node.get("input"), EngineInput.class),
                    node.get("defaulted").asBoolean());
        } catch (Exception e) {
            throw new IllegalStateException("Malformed history line: " + e.getMessage(), e);
        }
    }

    private int ingestChunk(List<SeedRecord> chunk, RuleConfig config, Totals totals) {
        List<SeedRecord> batch = List.copyOf(chunk);
        txTemplate.executeWithoutResult(status -> {
            for (SeedRecord record : batch) {
                if (record.i() >= MAX_RECORDS) {
                    throw new IllegalStateException(
                            "Seed count exceeds " + MAX_RECORDS + " (SSN space 990000000–990999999)");
                }
                ApplicationEntity app = applicationRepository.save(buildApplication(record));
                Decision decision = DecisionEngine.evaluate(record.input(), config);
                ledgerWriter.appendAt(ledgerEntry(app, record.input(), decision), record.createdAt());
                boolean approved = decision.outcome() == Outcome.APPROVED;
                jdbc.update(LOAN_OUTCOME_INSERT, app.getId(), record.defaulted(), !approved);
                totals.add(approved, record.defaulted());
            }
        });
        return batch.size();
    }

    private ApplicationEntity buildApplication(SeedRecord record) {
        EngineInput input = record.input();
        String ssn = "990" + String.format("%06d", record.i());
        String name = "Seed Applicant " + record.i();

        ApplicationEntity app = new ApplicationEntity();
        app.setPublicId("SEED-" + record.i());   // never draws from application_public_seq (keeps demo ids 1041+)
        app.setClientId("seed@parallax.dev");
        app.setName(name);
        app.setNameMasked(NameMasker.mask(name));
        app.setSsn(ssn);
        app.setSsnToken(tokenizer.ssnToken(ssn));
        app.setSsnLast4(ssn.substring(ssn.length() - 4));
        app.setDob(LocalDate.of(input.birthYear(), 1, 15).toString());
        app.setBirthYear(input.birthYear());
        app.setAddress("Seed address " + record.i());
        app.setAnnualIncome(input.annualIncome());
        app.setMonthlyHousing(input.monthlyHousing());
        app.setMonthlyDebt(input.monthlyDebt());
        app.setIndependentIncome(input.independentIncome());
        app.setBureauConsent(input.bureauConsent());
        app.setProduct(PRODUCTS[record.i() % PRODUCTS.length]);
        app.setInitialStatus(ApplicationStatus.DECIDED);
        app.setSource("SEED");
        app.setEngineAttempts(0);
        app.setCreatedAt(record.createdAt());
        app.setUpdatedAt(record.createdAt());
        return app;
    }

    private LedgerEntry ledgerEntry(ApplicationEntity app, EngineInput input, Decision decision) {
        return LedgerEntry.builder(LedgerKind.DECISION, LedgerSource.SEED)
                .application(app.getId(), app.getPublicId())
                .ruleVersion("v1.3")
                .scorecardVersion(ScorecardVersion.VALUE)
                .engineVersion(EngineVersion.VALUE)
                .engineInput(input)
                .outcome(decision.outcome().name())
                .score(decision.score())
                .creditLimit(decision.creditLimit())
                .reasonCodes(names(decision.reasonCodes()))
                .fraudFlags(names(decision.fraudFlags()))
                .atpMax(decision.atpMax())
                .build();
    }

    private static List<String> names(List<ReasonCode> codes) {
        return codes.stream().map(Enum::name).toList();
    }

    private void logProgress(long processed) {
        if (processed % 10000 == 0) {
            log.info("Seeded {} records...", processed);
        }
    }

    private static final class Totals {
        private long approved;
        private long defaults;

        void add(boolean approved, boolean defaulted) {
            if (approved) {
                this.approved++;
            }
            if (defaulted) {
                this.defaults++;
            }
        }
    }
}
