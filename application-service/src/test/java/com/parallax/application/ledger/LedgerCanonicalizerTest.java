package com.parallax.application.ledger;

import com.parallax.application.json.CanonicalJson;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** Golden test for the canonical payload: exact key order, no whitespace, nulls kept (SPEC §5). */
class LedgerCanonicalizerTest {

    private final LedgerCanonicalizer canonicalizer = new LedgerCanonicalizer(new CanonicalJson());

    @Test
    void payloadHasTheExactSpecOrderAndFormat() {
        LedgerRecord record = new LedgerRecord(
                1L, LedgerKind.GOVERNANCE, LedgerSource.LIVE,
                null, null,
                null, null, null,
                null, null,
                null,
                null, null, null,
                null, null,
                null, null,
                null,
                Map.of("note", "v1.4 promoted to LIVE"),
                Instant.parse("2026-09-25T09:14:03.123456Z"),
                "0".repeat(64),
                "ignored");

        String expected = "{"
                + "\"applicationId\":null,"
                + "\"atpMax\":null,"
                + "\"bureauPullId\":null,"
                + "\"bureauReused\":null,"
                + "\"createdAt\":\"2026-09-25T09:14:03.123456Z\","
                + "\"creditLimit\":null,"
                + "\"engineInput\":null,"
                + "\"engineVersion\":null,"
                + "\"fraudFlags\":null,"
                + "\"governanceDetail\":{\"note\":\"v1.4 promoted to LIVE\"},"
                + "\"kind\":\"GOVERNANCE\","
                + "\"linkedSeq\":null,"
                + "\"outcome\":null,"
                + "\"overrideDetail\":null,"
                + "\"prevHash\":\"" + "0".repeat(64) + "\","
                + "\"reasonCodes\":null,"
                + "\"ruleVersion\":null,"
                + "\"score\":null,"
                + "\"scorecardVersion\":null,"
                + "\"seq\":1,"
                + "\"source\":\"LIVE\""
                + "}";

        assertThat(canonicalizer.payload(record)).isEqualTo(expected);
    }
}
