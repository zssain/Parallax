package com.parallax.assistant.config;

import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Wires the OpenAI chat model only when an API key is present (SPEC §12): with a blank key no
 * ChatModel bean exists, so the service starts, {@code status.configured} is false and chat returns 503.
 * The OpenAI auto-configuration is excluded (see AssistantServiceApplication) so this is the single
 * place the model is built.
 */
@Configuration
public class AssistantConfig {

    @Bean
    @ConditionalOnExpression("'${spring.ai.openai.api-key:}'.trim().length() > 0")
    public ChatModel openAiChatModel(@Value("${spring.ai.openai.api-key}") String apiKey,
                                     AssistantProperties properties) {
        OpenAiApi api = OpenAiApi.builder().apiKey(apiKey).build();
        OpenAiChatOptions options = OpenAiChatOptions.builder()
                .model(properties.getAssistant().getModel())
                .maxTokens(properties.getAssistant().getMaxTokens())
                .build();
        return OpenAiChatModel.builder().openAiApi(api).defaultOptions(options).build();
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
