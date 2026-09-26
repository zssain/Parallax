package com.parallax.application;

import com.parallax.application.security.ParallaxUsersProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
// Registered here (not on SecurityConfig) so the users config is available even in the non-web
// seed context, where the web-only SecurityConfig is not active (Prompt 12).
@EnableConfigurationProperties(ParallaxUsersProperties.class)
public class ApplicationServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(ApplicationServiceApplication.class, args);
    }
}
