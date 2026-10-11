package com.climb.api.service;

import com.climb.api.model.ParticipanteReuniao;
import com.climb.api.repository.ParticipanteReuniaoRepository;
import com.climb.api.repository.ReuniaoRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;

/** Aguarda a geração assíncrona do Google Meet antes de enviar os avisos com o link. */
@Service
public class ReuniaoConviteService {
    private static final Logger log = LoggerFactory.getLogger(ReuniaoConviteService.class);
    private final ReuniaoRepository reunioes;
    private final ParticipanteReuniaoRepository participantes;
    private final GoogleCredentialService credenciais;
    private final GoogleCalendarService google;
    private final ReuniaoEmailService avisos;
    private final TransactionTemplate transaction;

    public ReuniaoConviteService(ReuniaoRepository reunioes, ParticipanteReuniaoRepository participantes,
                                 GoogleCredentialService credenciais, GoogleCalendarService google,
                                 ReuniaoEmailService avisos, PlatformTransactionManager manager) {
        this.reunioes = reunioes; this.participantes = participantes; this.credenciais = credenciais;
        this.google = google; this.avisos = avisos; this.transaction = new TransactionTemplate(manager);
    }

    @Scheduled(cron = "${app.reunioes.convites.cron:0 * * * * *}", zone = "America/Sao_Paulo")
    public void processarPendentes() {
        reunioes.findIdsConvitesPendentes().forEach(id -> {
            try { transaction.executeWithoutResult(status -> processar(id)); }
            catch (Exception e) { log.warn("Convite da reunião {} aguardando sincronização com Google Agenda: {}", id, e.getMessage()); }
        });
    }

    private void processar(Long id) {
        var reuniao = reunioes.findByIdForUpdate(id).orElse(null);
        if (reuniao == null || reuniao.isConvitesNotificados() || "CANCELADA".equals(reuniao.getStatus())) return;
        String token = credenciais.obterAccessToken(reuniao.getOrganizadorId()).orElse(null);
        if (!StringUtils.hasText(token)) return;
        try {
            String meet = google.extrairLinkMeet(google.buscarEvento(reuniao.getGoogleEventId(), token));
            if (!StringUtils.hasText(meet)) return;
            reuniao.setLinkMeet(meet);
            avisos.notificarAgendamento(reuniao, participantes.findByReuniao_IdReuniao(id).stream()
                    .map(ParticipanteReuniao::getUsuario).distinct().toList());
            reunioes.save(reuniao);
        } catch (Exception e) { throw new IllegalStateException("Google Meet ainda indisponível", e); }
    }
}
