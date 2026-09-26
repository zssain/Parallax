package com.parallax.assistant.tools;

import com.parallax.assistant.client.ParallaxClient;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.assertj.core.api.Assertions.assertThat;

/** The tool registry exposes exactly the seven read-only tools (SPEC §12). */
class ToolRegistryTest {

    @Test
    void registersExactlyTheSevenTools() {
        ParallaxToolRegistry registry = new ParallaxToolRegistry(new ParallaxTools(Mockito.mock(ParallaxClient.class)));

        assertThat(registry.callbacks()).hasSize(7);
        assertThat(registry.names()).containsExactlyInAnyOrder(
                "getDecision", "getReasonCodes", "runReplay", "getReplayReport",
                "compareVersions", "getOverrideStats", "getDriftReport");
    }

    @Test
    void runReplayIsMarkedExistingReportsOnly() {
        ParallaxToolRegistry registry = new ParallaxToolRegistry(new ParallaxTools(Mockito.mock(ParallaxClient.class)));

        ParallaxToolRegistry.ToolView runReplay = registry.tools().stream()
                .filter(t -> t.name().equals("runReplay")).findFirst().orElseThrow();
        assertThat(runReplay.access()).isEqualTo("read · existing reports only");
        assertThat(registry.tools()).allSatisfy(t -> assertThat(t.access()).startsWith("read"));
    }
}
