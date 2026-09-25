package com.parallax.application.ledger;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * A stored decision-ledger row (SPEC §5, §14). {@code applicationPublicId} is resolved by joining the
 * application table; it is the {@code applicationId} value hashed into the canonical payload (the
 * table itself stores only the numeric {@code applicationDbId}).
 */
public record LedgerRecord(
        long seq,
        LedgerKind kind,
        LedgerSource source,
        Long applicationDbId,
        String applicationPublicId,
        String ruleVersion,
        String scorecardVersion,
        String engineVersion,
        String bureauPullId,
        Boolean bureauReused,
        Object engineInput,
        String outcome,
        Integer score,
        Integer creditLimit,
        List<String> reasonCodes,
        List<String> fraudFlags,
        Integer atpMax,
        Long linkedSeq,
        Map<String, Object> overrideDetail,
        Map<String, Object> governanceDetail,
        Instant createdAt,
        String prevHash,
        String hash) {
}
