package com.parallax.assistant.config;

import org.springframework.ai.anthropic.AnthropicChatModel;
import org.springframework.ai.anthropic.AnthropicChatOptions;
import org.springframework.ai.anthropic.api.AnthropicApi;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Wires the Anthropic chat model only when an API key is present (SPEC §12): with a blank key no
 * ChatModel bean exists, so the service starts, {@code status.configured} is false and chat returns 503.
 * The Anthropic auto-configuration is excluded (see AssistantServiceApplication) so this is the single
 * place the model is built.
 */
@Configuration
public class AssistantConfig {

    @Bean
    @ConditionalOnExpression("'${spring.ai.anthropic.api-key:}'.trim().length() > 0")
    public ChatModel anthropicChatModel(@Value("${spring.ai.anthropic.api-key}") String apiKey,
                                        AssistantProperties properties) {
        AnthropicApi api = AnthropicApi.builder().apiKey(apiKey).build();
        AnthropicChatOptions options = AnthropicChatOptions.builder()
                .model(properties.getAssistant().getModel())
                .maxTokens(properties.getAssistant().getMaxTokens())
                .build();
        return AnthropicChatModel.builder().anthropicApi(api).defaultOptions(options).build();
    }

    @Bean
    @ConditionalOnMissingBean
    public ToolCallingManager toolCallingManager() {
        return ToolCallingManager.builder().build();
    }

    @Bean
    public SystemMessage assistantSystemMessage(@Value("classpath:prompts/system.md") Resource resource)
            throws IOException {
        return new SystemMessage(resource.getContentAsString(StandardCharsets.UTF_8));
    }
}
