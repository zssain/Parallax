package com.parallax.generator;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.SplittableRandom;
import java.util.stream.IntStream;
import java.util.stream.Stream;

/**
 * Generates a deterministic stream of dated synthetic applicants spanning {@code days} up to
 * {@code asOf} (SPEC §12 data, Prompt 12). One {@link SplittableRandom} drives the whole stream, so
 * the same params reproduce byte-identical output. Records in the last {@code driftDays} carry the
 * configured drift; earlier records carry none.
 */
public final class HistoryGenerator {

    /** Generation parameters (count, seed, span in days, drift strength, drift window, as-of date). */
    public record HistoryParams(int count, long seed, int days, double drift, int driftDays, LocalDate asOf) {
    }

    /** One dated record: its index, its instant, and the generated applicant. */
    public record HistoryRecord(int i, Instant createdAt, GeneratedApplicant applicant) {
    }

    /**
     * A sequential, ordered stream — the shared {@link SplittableRandom} is consumed in record order,
     * so it must not be parallelised.
     */
    public Stream<HistoryRecord> generate(HistoryParams p) {
        SplittableRandom r = new SplittableRandom(p.seed());
        Instant startOfAsOf = p.asOf().atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant start = startOfAsOf.minusSeconds((long) p.days() * 86_400L);
        Instant driftCutoff = startOfAsOf.minusSeconds((long) p.driftDays() * 86_400L);
        long stepSeconds = (long) p.days() * 86_400L / p.count();
        int asOfYear = p.asOf().getYear();

        return IntStream.range(0, p.count()).mapToObj(i -> {
            Instant createdAt = start.plusSeconds((long) i * stepSeconds);
            double drift = createdAt.isBefore(driftCutoff) ? 0.0 : p.drift();
            return new HistoryRecord(i, createdAt, ApplicantGenerator.next(r, drift, asOfYear));
        });
    }
}
