package com.parallax.application.seed;

import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.web.context.request.RequestScope;

/**
 * The seed profile runs with {@code web-application-type=none}, so the servlet request scope is not
 * registered automatically. This registers it manually so the demo seeder can drive the request-scoped
 * intake pipeline (bound via {@link SeedRequestAttributes}). Not active in integration tests, whose
 * web context already registers request scope.
 */
@Configuration
@Profile("seed")
public class SeedScopeConfig {

    @Bean
    static BeanFactoryPostProcessor seedRequestScope() {
        return beanFactory -> beanFactory.registerScope("request", new RequestScope());
    }
}
