package com.climb.api.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

@Configuration
public class GoogleDriveSchedulerConfig {
    @Bean(defaultCandidate = false)
    public ThreadPoolTaskScheduler googleDriveScheduler() {
        var scheduler = new ThreadPoolTaskScheduler(); scheduler.setPoolSize(1);
        scheduler.setThreadNamePrefix("google-drive-"); scheduler.setAwaitTerminationSeconds(35);
        return scheduler;
    }
}
