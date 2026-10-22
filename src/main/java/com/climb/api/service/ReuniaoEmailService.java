package com.climb.api.service;

import com.climb.api.model.Reuniao;
import com.climb.api.model.ParticipanteReuniao;
import com.climb.api.model.Usuario;
import com.climb.api.model.enums.RespostaConviteReuniao;
import org.springframework.stereotype.Service;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Objects;
import java.util.HashSet;

@Service
public class ReuniaoEmailService {
    private final NotificacaoEntregaService entrega;
    public ReuniaoEmailService(NotificacaoEntregaService entrega) { this.entrega = entrega; }

    public void notificarAgendamento(Reuniao reuniao, List<Usuario> convidados) {
        if (!Boolean.TRUE.equals(reuniao.getPresencial()) && (reuniao.getLinkMeet() == null || reuniao.getLinkMeet().isBlank())) {
            reuniao.setConvitesNotificados(false);
            return; // O Google gera o Meet de forma assíncrona; o processador envia o convite completo quando estiver pronto.
        }
        String mensagem = "Você foi convidado para a reunião \"" + reuniao.getTitulo() + "\".\n"
                + "Data: " + reuniao.getData().format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))
                + ". Horário: " + reuniao.getHora().format(DateTimeFormatter.ofPattern("HH:mm"))
                + " às " + reuniao.getDataHoraFim().format(DateTimeFormatter.ofPattern("HH:mm")) + ".\n"
                + (Boolean.TRUE.equals(reuniao.getPresencial()) ? "Local: " + reuniao.getLocal()
                : "Google Meet: " + (reuniao.getLinkMeet() == null ? "Disponível no convite do Google Agenda" : reuniao.getLinkMeet()));
        String destino = "/agenda?reuniaoId=" + reuniao.getIdReuniao();
        // O organizador participa para acessar a agenda, mas não recebe convite para a própria reunião.
        convidados.stream().filter(usuario -> !Objects.equals(usuario.getId(), reuniao.getOrganizadorId()))
                .forEach(usuario -> entrega.notificar(usuario, "REUNIAO_AGENDADA", "Convite para reunião", mensagem, destino));
        if (convidados.stream().noneMatch(u -> GoogleCalendarService.EMAIL_OBRIGATORIO.equalsIgnoreCase(u.getEmail()))) {
            entrega.avisarPorEmail(GoogleCalendarService.EMAIL_OBRIGATORIO, "Convite para reunião", mensagem, destino);
        }
        reuniao.setConvitesNotificados(true);
    }

    public void notificarCancelamento(Reuniao reuniao, List<ParticipanteReuniao> participantes) {
        String quando = reuniao.getData() == null ? "" : ", marcada para "
                + reuniao.getData().format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))
                + (reuniao.getHora() == null ? "" : " às " + reuniao.getHora().format(DateTimeFormatter.ofPattern("HH:mm"))) + ",";
        var notificados = new HashSet<Long>();
        for (var participante : participantes) {
            if (participante.getResposta() != RespostaConviteReuniao.ACEITA) continue;
            var usuario = participante.getUsuario();
            if (usuario == null || usuario.getId() == null || Objects.equals(usuario.getId(), reuniao.getOrganizadorId())
                    || !notificados.add(usuario.getId())) continue;

            String saudacao = usuario.getNomeCompleto() == null || usuario.getNomeCompleto().isBlank()
                    ? "Olá!" : "Olá, " + usuario.getNomeCompleto().strip().split("\\s+")[0] + "!";
            String mensagem = saudacao + " A reunião \"" + reuniao.getTitulo() + "\"" + quando
                    + " foi cancelada. Você pode liberar esse horário na sua agenda.";
            entrega.notificar(usuario, "REUNIAO_CANCELADA", "Reunião cancelada: " + reuniao.getTitulo(), mensagem,
                    "/agenda?reuniaoId=" + reuniao.getIdReuniao());
        }
    }
}
