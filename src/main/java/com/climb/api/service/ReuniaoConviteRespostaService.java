package com.climb.api.service;

import com.climb.api.model.ParticipanteReuniao;
import com.climb.api.model.Reuniao;
import com.climb.api.model.dto.ReuniaoListItemDTO;
import com.climb.api.model.enums.RespostaConviteReuniao;
import com.climb.api.repository.ParticipanteReuniaoRepository;
import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import com.google.api.services.calendar.model.Event;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

@Service
public class ReuniaoConviteRespostaService {
    private static final Logger log = LoggerFactory.getLogger(ReuniaoConviteRespostaService.class);
    private static final ZoneId ZONE = ZoneId.of("America/Sao_Paulo");
    private final ParticipanteReuniaoRepository participantes;
    private final GoogleCredentialService credentials;
    private final GoogleCalendarService calendar;
    private final TransactionTemplate transaction;

    public ReuniaoConviteRespostaService(ParticipanteReuniaoRepository participantes, GoogleCredentialService credentials,
                                        GoogleCalendarService calendar, PlatformTransactionManager manager) {
        this.participantes = participantes;
        this.credentials = credentials;
        this.calendar = calendar;
        this.transaction = new TransactionTemplate(manager);
    }

    @Transactional(readOnly = true)
    public List<ReuniaoListItemDTO> listarPendentes(Long usuarioId) {
        var agora = LocalDateTime.now(ZONE);
        return participantes.findConvitesPendentes(usuarioId, agora.toLocalDate()).stream()
                .filter(p -> p.getReuniao().getDataHoraFim() != null && p.getReuniao().getDataHoraFim().isAfter(agora))
                .map(this::resumo).toList();
    }

    @Transactional
    public ReuniaoListItemDTO aceitar(Long reuniaoId, Long usuarioId) {
        var convite = participantes.findConviteForUpdate(reuniaoId, usuarioId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN, "Você não foi convidado para esta reunião."));
        if ("CANCELADA".equals(convite.getReuniao().getStatus()) || "CANCELADO".equals(convite.getReuniao().getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Esta reunião foi cancelada.");
        }
        if (convite.getResposta() != RespostaConviteReuniao.ACEITA) {
            if (convite.getReuniao().getDataHoraFim() == null || !convite.getReuniao().getDataHoraFim().isAfter(LocalDateTime.now(ZONE))) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Esta reunião já terminou.");
            }
            convite.setResposta(RespostaConviteReuniao.ACEITA);
            convite.setRespondidoEm(LocalDateTime.now(ZONE));
            convite.setAceiteSincronizadoGoogle(false);
        }
        sincronizar(convite);
        participantes.save(convite);
        return resumo(convite);
    }

    @Scheduled(cron = "${app.reunioes.aceites.cron:15 * * * * *}", zone = "America/Sao_Paulo")
    public void sincronizarPendentesGoogle() {
        participantes.findIdsAceitesPendentesGoogle(LocalDateTime.now(ZONE).toLocalDate()).forEach(id -> {
            try {
                transaction.executeWithoutResult(status -> participantes.findByIdForUpdate(id).ifPresent(p -> {
                    if (p.getResposta() != RespostaConviteReuniao.ACEITA || p.isAceiteSincronizadoGoogle()) return;
                    sincronizar(p);
                    participantes.save(p);
                }));
            } catch (Exception e) {
                log.warn("Aceite da reunião aguardando sincronização: {}", e.getClass().getSimpleName());
            }
        });
    }

    private void sincronizar(ParticipanteReuniao convite) {
        if (convite.isAceiteSincronizadoGoogle()) return;
        if (convite.getReuniao().getGoogleEventId() == null) return;
        try {
            String token = credentials.obterAccessToken(convite.getUsuario().getId()).orElse(null);
            if (token == null || token.isBlank()) return;
            Event evento = eventoNaAgenda(convite.getReuniao(), token);
            calendar.aceitarConvite(evento, convite.getUsuario().getEmail(), token);
            convite.setAceiteSincronizadoGoogle(true);
        } catch (Exception e) {
            log.warn("Aceite registrado no sistema, aguardando Google Agenda: {}", e.getClass().getSimpleName());
        }
    }

    private Event eventoNaAgenda(Reuniao reuniao, String token) throws Exception {
        try {
            return calendar.buscarEvento(reuniao.getGoogleEventId(), token);
        } catch (GoogleJsonResponseException e) {
            if (e.getStatusCode() != 404 && e.getStatusCode() != 410) throw e;
            String organizadorToken = credentials.obterAccessToken(reuniao.getOrganizadorId()).orElseThrow();
            var original = calendar.buscarEvento(reuniao.getGoogleEventId(), organizadorToken);
            return calendar.buscarEventoPorICalUID(original.getICalUID(), token);
        }
    }

    private ReuniaoListItemDTO resumo(ParticipanteReuniao convite) {
        var dto = ReuniaoListItemDTO.fromEntity(convite.getReuniao());
        dto.setMinhaResposta(convite.getResposta());
        dto.setConviteId(convite.getId());
        dto.setRespondidoEm(convite.getRespondidoEm());
        return dto;
    }
}
