package com.parallax.application.ledger;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

class LedgerConcurrencyIT extends AbstractLedgerIT {

    @Test
    void twentyConcurrentAppendsProduceContiguousSeqs() throws Exception {
        long appId = insertApplication("APP-C");
        int threads = 20;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        try {
            List<Callable<LedgerRecord>> tasks = IntStream.range(0, threads)
                    .<Callable<LedgerRecord>>mapToObj(i -> () -> appendInTx(decisionEntry(appId, "APP-C")))
                    .toList();
            List<Future<LedgerRecord>> futures = pool.invokeAll(tasks, 30, TimeUnit.SECONDS);
            for (Future<LedgerRecord> f : futures) {
                f.get();
            }
        } finally {
            pool.shutdownNow();
        }

        List<Long> seqs = readAll().stream().map(LedgerRecord::seq).toList();
        assertThat(seqs).containsExactlyElementsOf(
                IntStream.rangeClosed(1, threads).mapToObj(Long::valueOf).toList());

        VerifyResult result = ledgerVerifier.verify();
        assertThat(result.ok()).isTrue();
        assertThat(result.checked()).isEqualTo(threads);
    }
}
