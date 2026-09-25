package com.parallax.application.ledger;

import com.parallax.engine.model.EngineInput;
import com.parallax.engine.model.PullType;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Regression for the v1 ledger bug: hashes computed before insert must still match after reading the
 * row back (Instant nanos vs Postgres micros; typed engine input vs raw jsonb text). SPEC §5.
 */
class LedgerRoundTripIT extends AbstractLedgerIT {

    @Test
    void fullEngineInputRoundTripsAndHashMatches() {
        long appId = insertApplication("APP-1041");
        EngineInput input = new EngineInput(30, 1996, 64000, 1350, 280, 0.08, 0, 0, 12, 156,
                true, true, false, 1997, false, 1, PullType.HARD);
        LedgerRecord written = appendInTx(LedgerEntry.builder(LedgerKind.DECISION, LedgerSource.LIVE)
                .application(appId, "APP-1041")
                .ruleVersion("v1.3").scorecardVersion("sc-2.1").engineVersion("engine-1.0.0")
                .bureau("BP-ABCD1234", false)
                .engineInput(input)
                .outcome("APPROVED").score(830).creditLimit(12000)
                .reasonCodes(List.of()).fraudFlags(List.of()).atpMax(29200)
                .build());

        LedgerRecord read = readAll().get(0);
        assertThat(read.engineInput()).isEqualTo(input);
        assertThat(recomputeHash(read)).isEqualTo(written.hash());
    }

    @Test
    void tripleDecimalUtilizationRoundTrips() {
        long appId = insertApplication("APP-1042");
        EngineInput input = new EngineInput(45, 1981, 45000, 1200, 300, 0.555, 3, 0, 5, 40,
                true, true, false, 1982, false, 1, PullType.HARD);
        LedgerRecord written = appendInTx(LedgerEntry.builder(LedgerKind.DECISION, LedgerSource.LIVE)
                .application(appId, "APP-1042")
                .ruleVersion("v1.3").scorecardVersion("sc-2.1").engineVersion("engine-1.0.0")
                .bureau("BP-EEEE2222", false)
                .engineInput(input)
                .outcome("REFER").score(655).creditLimit(0)
                .reasonCodes(List.of("R31", "R14", "R05", "R33")).fraudFlags(List.of()).atpMax(12200)
                .build());

        LedgerRecord read = readAll().get(0);
        assertThat(((EngineInput) read.engineInput()).revolvingUtilization()).isEqualTo(0.555);
        assertThat(recomputeHash(read)).isEqualTo(written.hash());
    }

    @Test
    void partialEngineInputRoundTrips() {
        long appId = insertApplication("APP-1043");
        PartialEngineInput partial = PartialEngineInput.unavailable(31, 1995, 38000, 1300, 520,
                true, true, PullType.HARD);
        LedgerRecord written = appendInTx(LedgerEntry.builder(LedgerKind.DECISION, LedgerSource.LIVE)
                .application(appId, "APP-1043")
                .ruleVersion("v1.3").scorecardVersion("sc-2.1").engineVersion("engine-1.0.0")
                .engineInput(partial)
                .outcome("REFER").reasonCodes(List.of("B01")).fraudFlags(List.of()).creditLimit(0)
                .build());

        LedgerRecord read = readAll().get(0);
        assertThat(read.engineInput()).isInstanceOf(PartialEngineInput.class).isEqualTo(partial);
        assertThat(recomputeHash(read)).isEqualTo(written.hash());
    }

    @Test
    void governanceRowWithNullInputRoundTrips() {
        LedgerRecord written = appendInTx(LedgerEntry.builder(LedgerKind.GOVERNANCE, LedgerSource.LIVE)
                .governanceDetail(Map.of("note", "v1.4 promoted to LIVE"))
                .build());

        LedgerRecord read = readAll().get(0);
        assertThat(read.engineInput()).isNull();
        assertThat(read.governanceDetail()).containsEntry("note", "v1.4 promoted to LIVE");
        assertThat(recomputeHash(read)).isEqualTo(written.hash());
    }
}
