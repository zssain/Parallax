package com.parallax.assistant;

import com.parallax.assistant.config.AssistantProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

/**
 * The read-only assistant service (SPEC §12). All OpenAI model auto-configurations are excluded (see
 * spring.autoconfigure.exclude in application.yml) because they require the API key at startup; the chat
 * model is built only when a key is present ({@code AssistantConfig}), so the service always starts and
 * reports {@code status.configured} accordingly.
 */
@SpringBootApplication
@EnableConfigurationProperties(AssistantProperties.class)
public class AssistantServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(AssistantServiceApplication.class, args);
    }
}
