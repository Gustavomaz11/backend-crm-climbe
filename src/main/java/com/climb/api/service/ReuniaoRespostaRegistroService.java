package com.climb.api.service;

import com.climb.api.model.Notificacao;
import com.climb.api.model.ParticipanteReuniao;
import com.climb.api.model.enums.RespostaConviteReuniao;
import com.climb.api.repository.UsuarioRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Objects;

/** Chamado com o participante bloqueado; resposta e aviso são gravados na mesma transação. */
@Service
public class ReuniaoRespostaRegistroService {
    private final UsuarioRepository usuarios;
    private final NotificacaoService notificacoes;
    private final ApplicationEventPublisher eventos;

    public ReuniaoRespostaRegistroService(UsuarioRepository usuarios, NotificacaoService notificacoes,
                                         ApplicationEventPublisher eventos) {
        this.usuarios = usuarios;
        this.notificacoes = notificacoes;
        this.eventos = eventos;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void registrar(ParticipanteReuniao participante, RespostaConviteReuniao resposta, boolean sincronizadaGoogle) {
        var reuniao = participante.getReuniao();
        boolean organizador = Objects.equals(reuniao.getOrganizadorId(), participante.getUsuario().getId());
        if (organizador) resposta = RespostaConviteReuniao.ACEITA;
        if (participante.getResposta() == resposta) {
            if (sincronizadaGoogle) participante.setAceiteSincronizadoGoogle(true);
            return;
        }
        participante.setResposta(resposta);
        participante.setRespondidoEm(LocalDateTime.now(ZoneId.of("America/Sao_Paulo")));
        participante.setAceiteSincronizadoGoogle(sincronizadaGoogle || organizador);
        eventos.publishEvent(new AvisoAtualizado(null, reuniao.getIdReuniao(), AvisoAtualizado.Tipo.CONVITES));
        if (organizador || !resposta.definitiva() || reuniao.getOrganizadorId() == null) return;

        boolean aceita = resposta == RespostaConviteReuniao.ACEITA;
        usuarios.findById(reuniao.getOrganizadorId()).ifPresent(destinatario -> {
            var aviso = new Notificacao();
            aviso.setUsuario(destinatario);
            aviso.setTipo(aceita ? "REUNIAO_ACEITA" : "REUNIAO_RECUSADA");
            var convidado = participante.getUsuario();
            String nome = convidado.getNomeCompleto() == null || convidado.getNomeCompleto().isBlank()
                    ? convidado.getEmail() : convidado.getNomeCompleto();
            String quando = reuniao.getData() == null ? "" : " em " + reuniao.getData().format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))
                    + (reuniao.getHora() == null ? "" : " às " + reuniao.getHora().format(DateTimeFormatter.ofPattern("HH:mm")));
            aviso.setMensagem(nome + (aceita ? " confirmou presença" : " recusou o convite")
                    + " na reunião \"" + reuniao.getTitulo() + "\"" + quando + ".");
            aviso.setDestino("/agenda?reuniaoId=" + reuniao.getIdReuniao());
            notificacoes.criar(aviso);
        });
    }
}
