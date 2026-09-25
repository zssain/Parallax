package com.parallax.application.query;

import java.time.Instant;
import java.util.List;

/** Response DTOs for the ledger APIs (SPEC §15). */
public final class LedgerViews {

    private LedgerViews() {
    }

    public record ListResponse(List<ListItem> items, int page, int size, long total) {
    }

    public record ListItem(long seq, String kind, String source, String applicationId, String displayName,
                           String note, String outcome, String ruleVersion, Instant createdAt,
                           String prevHash, String hash) {
    }

    public record Stats(long records, long decisions, long overrides, long governance) {
    }

    public record DemoUpdate(String statement, String error) {
    }

    public record TamperSimulation(long modifiedSeq, Long brokenAtSeq, int checked) {
    }
}
