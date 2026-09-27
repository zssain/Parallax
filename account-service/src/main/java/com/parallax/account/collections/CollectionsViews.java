package com.parallax.account.collections;

import java.time.Instant;
import java.util.List;

/** Response shapes for collections (SPEC §15). */
public final class CollectionsViews {

    private CollectionsViews() {
    }

    /** GET /api/v1/collections/summary: always the four buckets, zero when empty. */
    public record CollectionsSummary(List<BucketSummary> buckets) {
    }

    public record BucketSummary(String bucket, long count, long amountDueCents) {
    }

    /** A row of the collections work queue. */
    public record CollectionItem(
            String accountId,
            String displayName,
            int daysPastDue,
            String bucket,
            long amountDueCents,
            long balanceCents,
            Instant lastContactAt,
            String priority) {
    }
}
