package com.parallax.application.intake;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class IntakeIdempotencyIT extends AbstractIntakeIT {

    private static final String USER = "priya.menon@parallax.dev";
    private static final String SSN = "912345678";

    @Test
    void sameKeyAndBodyReplaysTheStoredResponse() throws Exception {
        stubBureau(SSN, primeResponse("BP-REPLAY", "48 Elm Street, Columbus OH"));
        String key = newKey();
        Map<String, Object> body = defaultRequest();

        String firstBody = submit(USER, key, body)
                .andExpect(status().isAccepted())
                .andReturn().getResponse().getContentAsString();

        String replayBody = submit(USER, key, body)
                .andExpect(status().isOk())
                .andExpect(header().string("Idempotent-Replay", "true"))
                .andReturn().getResponse().getContentAsString();

        // Identical content; response_body is stored as jsonb, which normalizes key order/whitespace.
        assertThat(objectMapper.readTree(replayBody)).isEqualTo(objectMapper.readTree(firstBody));
        assertThat(count("application")).isEqualTo(1);
    }

    @Test
    void sameKeyDifferentBodyIsRejected() throws Exception {
        stubBureau(SSN, primeResponse("BP-DIFF", "48 Elm Street, Columbus OH"));
        String key = newKey();
        submit(USER, key, defaultRequest()).andExpect(status().isAccepted());

        Map<String, Object> different = defaultRequest();
        different.put("annualIncome", 70000);
        submit(USER, key, different).andExpect(status().isUnprocessableEntity());
    }

    @Test
    void twoConcurrentRequestsWithTheSameKeyGiveOneAcceptedAndOneConflict() throws Exception {
        stubBureauDelayed(SSN, primeResponse("BP-RACE", "48 Elm Street, Columbus OH"), 500);
        String key = newKey();
        Map<String, Object> body = defaultRequest();

        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch go = new CountDownLatch(1);
        Callable<Integer> task = () -> {
            ready.countDown();
            go.await();
            return submit(USER, key, body).andReturn().getResponse().getStatus();
        };
        try {
            Future<Integer> a = pool.submit(task);
            Future<Integer> b = pool.submit(task);
            ready.await(5, TimeUnit.SECONDS);
            go.countDown();
            List<Integer> statuses = List.of(a.get(15, TimeUnit.SECONDS), b.get(15, TimeUnit.SECONDS));
            assertThat(statuses).containsExactlyInAnyOrder(202, 409);
        } finally {
            pool.shutdownNow();
        }
        assertThat(count("application")).isEqualTo(1);
    }
}
