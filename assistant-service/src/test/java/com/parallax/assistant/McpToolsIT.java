package com.parallax.assistant;

import io.modelcontextprotocol.server.McpServerFeatures;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.ResolvableType;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.TestPropertySource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Boots the service and asserts the MCP server publishes exactly the seven read-only tools (SPEC §12).
 * It inspects the {@code List<SyncToolSpecification>} the Spring AI MCP server autoconfiguration builds
 * from our {@code ToolCallbackProvider} — that list <em>is</em> the MCP tool catalogue served over the
 * protocol (a live client handshake against {@code /sse} returns the same names; see the README curl and
 * {@code scripts}). No model key is needed: the tool catalogue never calls the LLM.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = "spring.ai.anthropic.api-key=")
class McpToolsIT {

    private static final List<String> SEVEN_TOOLS = List.of(
            "getDecision", "getReasonCodes", "runReplay", "getReplayReport",
            "compareVersions", "getOverrideStats", "getDriftReport");

    @Autowired
    ApplicationContext context;

    @Test
    void mcpServerPublishesExactlyTheSevenReadOnlyTools() {
        ObjectProvider<List<McpServerFeatures.SyncToolSpecification>> provider = context.getBeanProvider(
                ResolvableType.forClassWithGenerics(List.class, McpServerFeatures.SyncToolSpecification.class));

        // Aggregate every sync-tool-specification list the MCP autoconfiguration built (the ToolCallback
        // conversion of our provider, plus the empty annotation-scanner list) — this is the tool catalogue
        // the McpSyncServer serves over the protocol.
        List<String> toolNames = provider.stream()
                .flatMap(List::stream)
                .map(spec -> spec.tool().name())
                .toList();

        assertThat(toolNames).containsExactlyInAnyOrderElementsOf(SEVEN_TOOLS);
    }
}
