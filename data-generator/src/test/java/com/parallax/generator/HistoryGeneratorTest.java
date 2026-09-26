package com.parallax.generator;

import com.parallax.generator.HistoryGenerator.HistoryParams;
import com.parallax.generator.HistoryGenerator.HistoryRecord;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class HistoryGeneratorTest {

    private static final LocalDate AS_OF = LocalDate.of(2026, 9, 25);
    private final HistoryGenerator generator = new HistoryGenerator();

    @Test
    void sameParamsProduceIdenticalJsonlBytes() {
        HistoryParams params = new HistoryParams(3000, 20260925L, 365, 0.35, 60, AS_OF);
        String first = jsonl(generator.generate(params).toList());
        String second = jsonl(generator.generate(params).toList());
        assertThat(second).isEqualTo(first);
    }

    @Test
    void createdAtIsStrictlyIncreasing() {
        List<HistoryRecord> records = generator.generate(
                new HistoryParams(3000, 1L, 365, 0.35, 60, AS_OF)).toList();
        Instant previous = null;
        for (HistoryRecord record : records) {
            if (previous != null) {
                assertThat(record.createdAt()).isAfter(previous);
            }
            previous = record.createdAt();
        }
    }

    @Test
    void driftAffectsOnlyTheLastDriftDays() {
        HistoryParams withDrift = new HistoryParams(3000, 9L, 365, 0.35, 60, AS_OF);
        HistoryParams noDrift = new HistoryParams(3000, 9L, 365, 0.0, 60, AS_OF);
        List<HistoryRecord> a = generator.generate(withDrift).toList();
        List<HistoryRecord> b = generator.generate(noDrift).toList();

        // Drift never changes the number of random draws, so the oldest record (outside the window)
        // is identical with and without drift, while the newest record (inside the window) differs.
        assertThat(a.get(0)).isEqualTo(b.get(0));
        assertThat(a.get(a.size() - 1)).isNotEqualTo(b.get(b.size() - 1));

        Instant cutoff = AS_OF.atStartOfDay(java.time.ZoneOffset.UTC).toInstant()
                .minusSeconds(60L * 86_400L);
        assertThat(a.get(a.size() - 1).createdAt()).isAfterOrEqualTo(cutoff);
    }

    private static String jsonl(List<HistoryRecord> records) {
        StringBuilder sb = new StringBuilder();
        for (HistoryRecord record : records) {
            sb.append(HistoryJsonWriter.line(record)).append('\n');
        }
        return sb.toString();
    }
}
