package com.climb.api.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

@Configuration
public class CnpjaComprovantesConfig {
    @Bean(defaultCandidate = false)
    public ThreadPoolTaskScheduler cnpjaComprovantesScheduler() {
        var scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(1);
        scheduler.setThreadNamePrefix("cnpja-comprovantes-");
        scheduler.setAwaitTerminationSeconds(35);
        return scheduler;
    }
}
