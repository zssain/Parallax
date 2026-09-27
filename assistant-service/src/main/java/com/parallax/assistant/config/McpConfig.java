package com.parallax.assistant.config;

import com.parallax.assistant.tools.ParallaxToolRegistry;
import org.springframework.ai.tool.StaticToolCallbackProvider;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Exposes the assistant's seven read-only tools (SPEC §12) over the Model Context Protocol. The Spring AI
 * MCP server starter (WebMVC/SSE transport) discovers any {@link ToolCallbackProvider} bean and publishes
 * its callbacks as MCP tools. We hand it the very same {@code ParallaxToolRegistry} callbacks the chat API
 * uses, so an MCP client sees exactly the same tools — and, like the chat API, only read access: the
 * assistant's own ASSISTANT credentials go out to application-service, so MCP clients are read-only by
 * construction, never by prompt.
 */
@Configuration
public class McpConfig {

    @Bean
    public ToolCallbackProvider parallaxMcpTools(ParallaxToolRegistry registry) {
        return new StaticToolCallbackProvider(registry.callbacks());
    }
}
