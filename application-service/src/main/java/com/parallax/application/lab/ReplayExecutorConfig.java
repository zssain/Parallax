package com.parallax.application.lab;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/** The dedicated single-thread "replay" executor (SPEC §10): core 1, max 1, queue 5. */
@Configuration
public class ReplayExecutorConfig {

    @Bean(name = "replayExecutor")
    public ThreadPoolTaskExecutor replayExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setThreadNamePrefix("replay-");
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(1);
        executor.setQueueCapacity(5);
        executor.initialize();
        return executor;
    }
}
