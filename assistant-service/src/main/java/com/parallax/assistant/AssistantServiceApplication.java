package com.parallax.assistant;

import com.parallax.assistant.config.AssistantProperties;
import org.springframework.ai.model.anthropic.autoconfigure.AnthropicChatAutoConfiguration;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

/**
 * The read-only assistant service (SPEC §12). The Anthropic chat auto-configuration is excluded so the
 * model is built only when an API key is present ({@code AssistantConfig}); the service always starts.
 */
@SpringBootApplication(exclude = AnthropicChatAutoConfiguration.class)
@EnableConfigurationProperties(AssistantProperties.class)
public class AssistantServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(AssistantServiceApplication.class, args);
    }
}
