package com.climb.api.security;

import com.climb.api.repository.NotificacaoRepository;
import org.springframework.stereotype.Component;

@Component("notificacaoAcesso")
public class NotificacaoAcesso {
    private final NotificacaoRepository notificacoes;
    private final PermissaoAcesso permissoes;

    public NotificacaoAcesso(NotificacaoRepository notificacoes, PermissaoAcesso permissoes) {
        this.notificacoes = notificacoes;
        this.permissoes = permissoes;
    }

    public boolean podeAcessar(Long id) {
        return notificacoes.findById(id).filter(n -> n.getUsuario() != null)
                .map(n -> permissoes.proprioOuAdministrador(n.getUsuario().getId())).orElse(false);
    }
}
