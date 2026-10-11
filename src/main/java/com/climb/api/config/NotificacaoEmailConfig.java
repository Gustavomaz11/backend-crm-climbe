package com.climb.api.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

@Configuration
public class NotificacaoEmailConfig {
    @Bean(name = "notificationEmailScheduler", defaultCandidate = false)
    public ThreadPoolTaskScheduler notificationEmailScheduler() {
        var scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(1);
        scheduler.setThreadNamePrefix("notification-email-");
        scheduler.setExecuteExistingDelayedTasksAfterShutdownPolicy(false);
        // O encerramento coordenado do Spring também libera executores pausados.
        scheduler.setAwaitTerminationSeconds(35);
        return scheduler;
    }
}
