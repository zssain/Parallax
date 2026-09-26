package com.parallax.generator;

import com.parallax.engine.model.EngineInput;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.SplittableRandom;

import static org.assertj.core.api.Assertions.assertThat;

class ApplicantGeneratorTest {

    @Test
    void sameSeedProducesIdenticalApplicants() {
        List<GeneratedApplicant> a = draw(1000, 42L);
        List<GeneratedApplicant> b = draw(1000, 42L);
        assertThat(a).isEqualTo(b);
    }

    @Test
    void differentSeedsDiffer() {
        assertThat(draw(1000, 42L)).isNotEqualTo(draw(1000, 43L));
    }

    @Test
    void everyGeneratedInputIsAValidEngineInput() {
        SplittableRandom r = new SplittableRandom(7L);
        for (int i = 0; i < 5000; i++) {
            // The EngineInput compact constructor validates ranges; a throw here fails the test.
            EngineInput input = ApplicantGenerator.next(r, i % 2 == 0 ? 0.0 : 0.35, 2026).input();
            assertThat(input.revolvingUtilization()).isBetween(0.0, 0.99);
            assertThat(input.annualIncome()).isPositive();
            assertThat(input.pullType()).isNotNull();
        }
    }

    private static List<GeneratedApplicant> draw(int n, long seed) {
        SplittableRandom r = new SplittableRandom(seed);
        List<GeneratedApplicant> out = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            out.add(ApplicantGenerator.next(r, 0.2, 2026));
        }
        return out;
    }
}
