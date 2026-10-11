package com.climb.api.config;

import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import java.time.Duration;
import java.time.Instant;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.awaitility.Awaitility.await;

class NotificacaoEmailConfigTest {
    @Test
    void encerrarServidorNaoEsperaEnviosQueAindaNaoComecaram() {
        var scheduler = new NotificacaoEmailConfig().notificationEmailScheduler();
        scheduler.initialize();
        try {
            var futuro = scheduler.schedule(() -> {}, Instant.now().plusSeconds(3600));
            assertTimeoutPreemptively(Duration.ofSeconds(3), scheduler::shutdown);
            assertThat(futuro.isCancelled()).isTrue();
            assertThat(scheduler.getScheduledExecutor().isTerminated()).isTrue();
        } finally {
            scheduler.getScheduledExecutor().shutdownNow();
        }
    }

    @Test
    void contextoPausadoEncerraSemEsperarUmaTarefaBloqueadaAntesDeExecutar() {
        var contexto = new AnnotationConfigApplicationContext(NotificacaoEmailConfig.class);
        var scheduler = contexto.getBean("notificationEmailScheduler", ThreadPoolTaskScheduler.class);
        try {
            scheduler.stop();
            scheduler.schedule(() -> {}, Instant.now());
            await().atMost(Duration.ofSeconds(3)).until(() -> scheduler.getActiveCount() == 1);
            assertTimeoutPreemptively(Duration.ofSeconds(3), contexto::close);
            assertThat(scheduler.getScheduledExecutor().isTerminated()).isTrue();
        } finally {
            scheduler.getScheduledExecutor().shutdownNow();
            contexto.close();
        }
    }
}
