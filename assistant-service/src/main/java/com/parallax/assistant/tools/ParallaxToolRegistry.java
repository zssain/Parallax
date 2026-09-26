package com.parallax.assistant.tools;

import org.springframework.ai.support.ToolCallbacks;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;

/**
 * The fixed registry of the assistant's tools (SPEC §12): exactly the seven read-only {@link ParallaxTools}
 * methods, and nothing else. Also renders the tools panel ({name, description, access}).
 */
@Component
public class ParallaxToolRegistry {

    private final ToolCallback[] callbacks;

    public ParallaxToolRegistry(ParallaxTools tools) {
        this.callbacks = ToolCallbacks.from(tools);
    }

    public ToolCallback[] callbacks() {
        return callbacks;
    }

    public List<String> names() {
        return Arrays.stream(callbacks).map(c -> c.getToolDefinition().name()).sorted().toList();
    }

    public List<ToolView> tools() {
        return Arrays.stream(callbacks)
                .map(c -> new ToolView(c.getToolDefinition().name(), c.getToolDefinition().description(),
                        access(c.getToolDefinition().name())))
                .toList();
    }

    private static String access(String name) {
        return "runReplay".equals(name) ? "read · existing reports only" : "read";
    }

    /** One row of the tools panel. */
    public record ToolView(String name, String description, String access) {
    }
}
