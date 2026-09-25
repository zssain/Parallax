package com.parallax.application.ledger;

import com.parallax.application.json.CanonicalJson;
import org.springframework.stereotype.Component;

import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

/**
 * Produces the canonical JSON payload hashed into each ledger row (SPEC §5): exactly the 21 keys,
 * sorted alphabetically, no whitespace, nulls kept. {@code createdAt} is written with exactly six
 * fraction digits (microseconds) and {@code engineInput} from the typed record — the two v1 bugs.
 */
@Component
public class LedgerCanonicalizer {

    /** Always six fraction digits, UTC, trailing 'Z' — never Instant's variable-precision default. */
    private static final DateTimeFormatter CREATED_AT =
            DateTimeFormatter.ofPattern("uuuu-MM-dd'T'HH:mm:ss.SSSSSS'Z'").withZone(ZoneOffset.UTC);

    private final CanonicalJson canonicalJson;

    public LedgerCanonicalizer(CanonicalJson canonicalJson) {
        this.canonicalJson = canonicalJson;
    }

    /** Canonical JSON of the record's hashed fields (everything except the hash itself). */
    public String payload(LedgerRecord r) {
        Map<String, Object> keys = new HashMap<>();
        keys.put("applicationId", r.applicationPublicId());
        keys.put("atpMax", r.atpMax());
        keys.put("bureauPullId", r.bureauPullId());
        keys.put("bureauReused", r.bureauReused());
        keys.put("createdAt", CREATED_AT.format(r.createdAt()));
        keys.put("creditLimit", r.creditLimit());
        keys.put("engineInput", r.engineInput());
        keys.put("engineVersion", r.engineVersion());
        keys.put("fraudFlags", r.fraudFlags());
        keys.put("governanceDetail", r.governanceDetail());
        keys.put("kind", r.kind().name());
        keys.put("linkedSeq", r.linkedSeq());
        keys.put("outcome", r.outcome());
        keys.put("overrideDetail", r.overrideDetail());
        keys.put("prevHash", r.prevHash());
        keys.put("reasonCodes", r.reasonCodes());
        keys.put("ruleVersion", r.ruleVersion());
        keys.put("score", r.score());
        keys.put("scorecardVersion", r.scorecardVersion());
        keys.put("seq", r.seq());
        keys.put("source", r.source().name());
        return canonicalJson.write(keys);
    }
}
