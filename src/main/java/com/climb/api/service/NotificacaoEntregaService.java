package com.climb.api.service;

import com.climb.api.model.Notificacao;
import com.climb.api.model.Usuario;
import com.climb.api.repository.NotificacaoRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Registra o aviso e o e-mail na mesma transação; o processador envia em segundo plano. */
@Service
@Transactional
public class NotificacaoEntregaService {
    private final NotificacaoRepository repository;
    private final EmailNotificacaoFilaService fila;

    public NotificacaoEntregaService(NotificacaoRepository repository, EmailNotificacaoFilaService fila) {
        this.repository = repository;
        this.fila = fila;
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
        fila.enfileirar(new EmailNotificacao(destinatario, assunto, mensagem, destino));
    }

    public record EmailNotificacao(String destinatario, String assunto, String mensagem, String destino) {}

}
