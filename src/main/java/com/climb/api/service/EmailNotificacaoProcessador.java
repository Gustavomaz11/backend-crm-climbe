package com.climb.api.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/** Executa em um agendador dedicado, sem bloquear requisições ou manter uma conexão SQL durante o SMTP. */
@Service
public class EmailNotificacaoProcessador {
    private static final Logger log = LoggerFactory.getLogger(EmailNotificacaoProcessador.class);
    private final EmailNotificacaoFilaService fila;
    private final EmailService email;
    private final String frontendUrl;
    private final boolean enabled;

    public EmailNotificacaoProcessador(EmailNotificacaoFilaService fila, EmailService email,
            @Value("${app.frontend-url:http://localhost:5173}") String frontendUrl,
            @Value("${app.notificacoes.email.enabled:true}") boolean enabled) {
        this.fila = fila; this.email = email; this.frontendUrl = frontendUrl.replaceAll("/+$", "");
        this.enabled = enabled;
    }

    @Scheduled(fixedDelayString = "${app.notificacoes.email.intervalo-ms:1000}",
            initialDelayString = "${app.notificacoes.email.intervalo-ms:1000}", scheduler = "notificationEmailScheduler")
    public void processarPendentes() {
        if (!enabled) return;
        for (int i = 0; i < 10; i++) {
            var envio = fila.reservar().orElse(null);
            if (envio == null) return;
            try {
                var evento = envio.email();
                boolean enviado = email.enviarEmailComBotao(evento.destinatario(), evento.assunto(), evento.assunto(),
                        evento.mensagem(), evento.destino() == null ? null : "Abrir no Climbe",
                        evento.destino() == null ? null : frontendUrl + evento.destino(),
                        "Esta é uma notificação automática da Climbe.");
                if (enviado) fila.confirmar(envio);
                else fila.reagendar(envio);
            } catch (Exception e) {
                fila.reagendar(envio);
                log.warn("Envio do e-mail de notificação {} adiado, tentativa {}", envio.id(), envio.tentativa(), e);
            }
        }
    }
}
