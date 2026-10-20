package com.climb.api.service;

import com.climb.api.model.enums.RespostaConviteReuniao;
import com.climb.api.model.dto.SalaReuniaoDTO;
import com.climb.api.repository.ParticipanteReuniaoRepository;
import com.climb.api.repository.ReuniaoRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.time.LocalDate;
import java.time.ZoneId;

/** Consulta a agenda do organizador e distribui as mudanças pelo SSE já utilizado no CRM. */
@Service
public class ReuniaoRespostasGoogleService {
    private static final Logger log = LoggerFactory.getLogger(ReuniaoRespostasGoogleService.class);
    private final ReuniaoRepository reunioes;
    private final ParticipanteReuniaoRepository participantes;
    private final GoogleCredentialService credentials;
    private final GoogleCalendarService calendar;
    private final ReuniaoRespostaRegistroService registro;
    private final TransactionTemplate transaction;

    public ReuniaoRespostasGoogleService(ReuniaoRepository reunioes, ParticipanteReuniaoRepository participantes,
            GoogleCredentialService credentials, GoogleCalendarService calendar, ReuniaoRespostaRegistroService registro,
            PlatformTransactionManager manager) {
        this.reunioes = reunioes;
        this.participantes = participantes;
        this.credentials = credentials;
        this.calendar = calendar;
        this.registro = registro;
        this.transaction = new TransactionTemplate(manager);
    }

    @Scheduled(cron = "${app.reunioes.respostas-google.cron:40 * * * * *}", zone = "America/Sao_Paulo")
    public void sincronizar() {
        reunioes.findIdsRespostasGoogle(LocalDate.now(ZoneId.of("America/Sao_Paulo"))).forEach(id -> {
            try {
                transaction.executeWithoutResult(status -> sincronizar(id));
            } catch (Exception e) {
                log.warn("Respostas da reunião {} aguardando Google Agenda: {}", id, e.getClass().getSimpleName());
            }
        });
    }

    private void sincronizar(Long id) {
        var reuniao = reunioes.findById(id).orElse(null);
        if (reuniao == null) return;
        var token = credentials.obterAccessToken(reuniao.getOrganizadorId()).orElse(null);
        if (token == null || token.isBlank()) return;
        com.google.api.services.calendar.model.Event evento;
        try {
            evento = calendar.buscarEvento(reuniao.getGoogleEventId(), token);
        } catch (Exception e) {
            throw new IllegalStateException("Não foi possível consultar as respostas no Google Agenda", e);
        }
        if (evento == null || "cancelled".equals(evento.getStatus()) || evento.getAttendees() == null) return;
        var convidados = evento.getAttendees().stream().filter(a -> !SalaReuniaoDTO.isResource(a)).toList();
        for (var participante : participantes.findByReuniao_IdReuniao(id)) {
            var email = participante.getUsuario().getEmail();
            var convidado = convidados.stream().filter(a -> email != null && email.equalsIgnoreCase(a.getEmail())).findFirst();
            // Listas omitidas e convidados ausentes não significam recusa.
            if (convidado.isEmpty()) continue;
            var resposta = RespostaConviteReuniao.fromGoogle(convidado.get().getResponseStatus());
            if (resposta == RespostaConviteReuniao.PENDENTE) continue;
            participantes.findByIdForUpdate(participante.getId()).ifPresent(atual -> {
                // Uma resposta registrada no CRM tem prioridade enquanto aguarda envio ao Google.
                if (atual.getResposta().definitiva() && !atual.isAceiteSincronizadoGoogle()) return;
                registro.registrar(atual, resposta, true);
                participantes.save(atual);
            });
        }
    }
}
