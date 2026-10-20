package com.climb.api.service;

import com.climb.api.model.KanbanPrazoNotificacao;
import com.climb.api.model.Notificacao;
import com.climb.api.repository.KanbanPrazoNotificacaoRepository;
import com.climb.api.repository.NotificacaoRepository;
import com.climb.api.repository.UsuarioRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;

/** Persiste os alertas internos independentemente do resultado do envio por e-mail. */
@Service
public class KanbanPrazoAvisoService {
    private final UsuarioRepository usuarios;
    private final NotificacaoRepository avisos;
    private final KanbanPrazoNotificacaoRepository registros;

    public KanbanPrazoAvisoService(UsuarioRepository usuarios, NotificacaoRepository avisos,
                                  KanbanPrazoNotificacaoRepository registros) {
        this.usuarios = usuarios; this.avisos = avisos; this.registros = registros;
    }

    @Transactional
    public void notificar(Long usuarioId, List<KanbanPrazoAtividade> atividades, LocalDate hoje) {
        if (atividades.isEmpty()) return;
        var usuario = usuarios.findByIdForUpdate(usuarioId).orElse(null);
        if (usuario == null || !"ATIVO".equals(usuario.getSituacao())) return;
        var porChave = new LinkedHashMap<String, KanbanPrazoAtividade>();
        atividades.forEach(a -> porChave.put("APP:" + a.chave(usuarioId), a));
        registros.buscarChaves(porChave.keySet()).forEach(porChave::remove);
        if (porChave.isEmpty()) return;
        porChave.values().forEach(atividade -> {
            var aviso = new Notificacao();
            aviso.setUsuario(usuario);
            aviso.setTipo(atividade.marco() < 0 ? "TAREFA_ATRASADA" : "TAREFA_VENCIMENTO");
            aviso.setMensagem(mensagem(atividade, hoje));
            URI link = URI.create(atividade.link());
            aviso.setDestino(link.getRawPath() + (link.getRawQuery() == null ? "" : "?" + link.getRawQuery()));
            avisos.save(aviso);
        });
        var agora = LocalDateTime.now(ZoneId.of("America/Sao_Paulo"));
        registros.saveAll(porChave.keySet().stream().map(chave -> new KanbanPrazoNotificacao(chave, agora)).toList());
    }

    private String mensagem(KanbanPrazoAtividade atividade, LocalDate hoje) {
        long dias = ChronoUnit.DAYS.between(hoje, atividade.prazo());
        String situacao = dias == 0 ? "vence hoje" : dias > 0
                ? "vence em " + dias + (dias == 1 ? " dia" : " dias")
                : "está atrasada há " + -dias + (dias == -1 ? " dia" : " dias");
        return "A tarefa \"" + atividade.titulo() + "\" " + situacao + ".\n"
                + "Empresa: " + atividade.empresa() + ".\n"
                + (atividade.servicos() == null || atividade.servicos().isBlank() ? "" : "Serviço: " + atividade.servicos() + ".\n")
                + "Responsáveis: " + atividade.responsaveis() + ".\n"
                + "Prazo: " + atividade.prazo().format(DateTimeFormatter.ofPattern("dd/MM/yyyy")) + ".\n"
                + "Acompanhe a tarefa para organizar os próximos passos.";
    }
}
