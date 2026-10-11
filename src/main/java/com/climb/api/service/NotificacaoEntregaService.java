package com.climb.api.service;

import com.climb.api.model.Notificacao;
import com.climb.api.model.Usuario;
import com.climb.api.repository.NotificacaoRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionalEventListener;

/** Registra o aviso junto da operação e envia e-mail somente depois de confirmada. */
@Service
public class NotificacaoEntregaService {
    private final NotificacaoRepository repository;
    private final ApplicationEventPublisher events;
    private final EmailService email;
    private final String frontendUrl;

    public NotificacaoEntregaService(NotificacaoRepository repository, ApplicationEventPublisher events,
                                     EmailService email,
                                     @Value("${app.frontend-url:http://localhost:5173}") String frontendUrl) {
        this.repository = repository;
        this.events = events;
        this.email = email;
        this.frontendUrl = frontendUrl.replaceAll("/+$", "");
    }

    public void notificar(Usuario usuario, String tipo, String assunto, String mensagem, String destino) {
        if (usuario == null || usuario.getId() == null) return;
        Notificacao notificacao = new Notificacao();
        notificacao.setUsuario(usuario);
        notificacao.setTipo(tipo);
        notificacao.setMensagem(mensagem);
        notificacao.setDestino(destino);
        repository.save(notificacao);
        avisarPorEmail(usuario.getEmail(), assunto, mensagem, destino);
    }

    public void avisarPorEmail(String destinatario, String assunto, String mensagem, String destino) {
        events.publishEvent(new EmailNotificacao(destinatario, assunto, mensagem, destino));
    }

    public record EmailNotificacao(String destinatario, String assunto, String mensagem, String destino) {}

    @TransactionalEventListener(fallbackExecution = true)
    public void enviarEmail(EmailNotificacao evento) {
        email.enviarEmailComBotao(evento.destinatario(), evento.assunto(), evento.assunto(), evento.mensagem(),
                evento.destino() == null ? null : "Abrir no Climbe",
                evento.destino() == null ? null : frontendUrl + evento.destino(),
                "Esta é uma notificação automática da Climbe.");
    }
}
