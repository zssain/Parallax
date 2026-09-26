package com.parallax.generator;

import com.parallax.engine.config.RuleConfigs;
import com.parallax.engine.model.Decision;
import com.parallax.engine.model.Outcome;
import com.parallax.engine.model.RuleConfig;
import com.parallax.engine.scoring.DecisionEngine;
import com.parallax.generator.HistoryGenerator.HistoryParams;
import com.parallax.generator.HistoryGenerator.HistoryRecord;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Sanity of the generated distribution under the LIVE config: a plausible approval rate and a
 * scorecard that actually separates risk (defaults concentrate below the cutoff). Prints the numbers.
 */
class DistributionSanityTest {

    @Test
    void distributionIsPlausibleAndSeparatesRisk() {
        RuleConfig config = RuleConfigs.v1_3();
        List<HistoryRecord> records = new HistoryGenerator().generate(
                new HistoryParams(20000, 20260925L, 365, 0.35, 60, LocalDate.of(2026, 9, 25))).toList();

        int n = records.size();
        int approvals = 0;
        int defaults = 0;
        int defaultsAmongApprovals = 0;
        for (HistoryRecord record : records) {
            boolean defaulted = record.applicant().defaulted();
            if (defaulted) {
                defaults++;
            }
            Decision decision = DecisionEngine.evaluate(record.applicant().input(), config);
            if (decision.outcome() == Outcome.APPROVED) {
                approvals++;
                if (defaulted) {
                    defaultsAmongApprovals++;
                }
            }
        }

        double approvalRate = (double) approvals / n;
        double defaultRate = (double) defaults / n;
        double defaultRateAmongApprovals = approvals == 0 ? 0.0 : (double) defaultsAmongApprovals / approvals;

        System.out.printf("DistributionSanity: n=%d approvalRate=%.4f defaultRate=%.4f "
                        + "defaultRateAmongApprovals=%.4f%n",
                n, approvalRate, defaultRate, defaultRateAmongApprovals);

        assertThat(approvalRate).isBetween(0.15, 0.90);
        assertThat(defaultRateAmongApprovals).isLessThan(defaultRate);
    }
}
