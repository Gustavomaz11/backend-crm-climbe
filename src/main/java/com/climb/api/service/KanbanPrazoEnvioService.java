package com.climb.api.service;

import com.climb.api.model.KanbanPrazoNotificacao;
import com.climb.api.repository.KanbanPrazoNotificacaoRepository;
import com.climb.api.repository.UsuarioRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

@Service
public class KanbanPrazoEnvioService {
    private final UsuarioRepository usuarios;
    private final KanbanPrazoNotificacaoRepository notificacoes;
    private final KanbanPrazoEmailService email;
    public KanbanPrazoEnvioService(UsuarioRepository usuarios, KanbanPrazoNotificacaoRepository notificacoes,
                                  KanbanPrazoEmailService email) {
        this.usuarios = usuarios; this.notificacoes = notificacoes; this.email = email;
    }

    @Transactional
    public void enviar(Long usuarioId, List<KanbanPrazoAtividade> atividades, LocalDate hoje) {
        if (atividades.isEmpty()) return;
        // Serializa os envios por destinatário, inclusive quando há várias instâncias da aplicação.
        var usuario = usuarios.findByIdForUpdate(usuarioId).orElse(null);
        if (usuario == null || !"ATIVO".equals(usuario.getSituacao()) || usuario.getEmail() == null || usuario.getEmail().isBlank()) return;
        var existentes = notificacoes.buscarChaves(atividades.stream().map(a -> a.chave(usuarioId)).toList());
        var pendentes = atividades.stream().filter(a -> !existentes.contains(a.chave(usuarioId))).toList();
        if (pendentes.isEmpty() || !email.enviar(usuario, pendentes, hoje)) return;
        var agora = LocalDateTime.now(ZoneId.of("America/Sao_Paulo"));
        notificacoes.saveAll(pendentes.stream().map(a -> new KanbanPrazoNotificacao(a.chave(usuarioId), agora)).toList());
    }
}
