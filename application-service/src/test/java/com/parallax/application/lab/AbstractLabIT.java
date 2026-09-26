package com.parallax.application.lab;

import com.fasterxml.jackson.databind.JsonNode;
import com.parallax.application.intake.AbstractIntakeIT;
import com.parallax.application.json.CanonicalJson;
import com.parallax.application.seed.HistorySeeder;
import com.parallax.engine.config.RuleConfigs;
import com.parallax.engine.model.RuleConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Shared setup for Strategy Lab replay ITs: seed 2,000 SEED records, insert candidates, run replays. */
abstract class AbstractLabIT extends AbstractIntakeIT {

    protected static final String STRATEGIST = "aditi.rao@parallax.dev";

    @Autowired
    protected HistorySeeder historySeeder;
    @Autowired
    protected CanonicalJson canonicalJson;

    @DynamicPropertySource
    static void seedProps(DynamicPropertyRegistry registry) {
        registry.add("parallax.seed.generate-count", () -> "2000");
    }

    /**
     * Keep rule_version to just the seeded v1.2/v1.3 before and after each test — it is not truncated by
     * the base, so candidate versions must not leak into other suites (e.g. MigrationIT).
     */
    @BeforeEach
    @AfterEach
    void clearCandidateVersions() {
        jdbc.update("DELETE FROM rule_version WHERE version NOT IN ('v1.2','v1.3')");
    }

    /** Seed 2,000 SEED history records with loan outcomes (evaluated under v1.3). */
    protected long seedHistory() {
        HistorySeeder.SeedSummary summary = historySeeder.seed();
        assertThat(summary.refused()).isFalse();
        return summary.count();
    }

    /** Insert a DRAFT candidate rule version directly (Prompt 14 adds the API). */
    protected void insertCandidate(String version, RuleConfig config) {
        String json = canonicalJson.write(config);
        String hash = canonicalJson.sha256Hex(json);
        jdbc.update("INSERT INTO rule_version (version, status, config, config_hash, created_by, created_at)"
                + " VALUES (?, 'DRAFT', ?::jsonb, ?, 'Aditi Rao', now())", version, json, hash);
    }

    /** v1.3 with a different approve cutoff — a typical candidate. */
    protected RuleConfig withApproveCutoff(int approveCutoff) {
        RuleConfig v = RuleConfigs.v1_3();
        return new RuleConfig(approveCutoff, v.referCutoff(), v.minPayPct(), v.atpShare(), v.livingCost(),
                v.minLimit(), v.bandLimits(), v.utilPts(), v.inqPts(), v.delqPts(), v.tradelinePts(),
                v.fileAgePts(), v.incomePts(), v.ccf(), v.lgd());
    }

    protected String startReplay(String user, String version) throws Exception {
        JsonNode body = read(postJsonAs(user, "/api/v1/lab/versions/" + version + "/replays", "{}")
                .andExpect(status().isAccepted()).andReturn());
        return body.get("jobId").asText();
    }

    /** Poll the job until DONE (fails on FAILED or timeout). Returns the final job view. */
    protected JsonNode pollDone(String jobId) throws Exception {
        for (int i = 0; i < 200; i++) {
            JsonNode job = read(getAs(STRATEGIST, "/api/v1/lab/replays/" + jobId).andReturn());
            String status = job.get("status").asText();
            if ("DONE".equals(status)) {
                return job;
            }
            if ("FAILED".equals(status)) {
                throw new AssertionError("replay " + jobId + " FAILED: " + job.get("error").asText());
            }
            Thread.sleep(100);
        }
        throw new AssertionError("replay " + jobId + " did not finish in time");
    }
}
