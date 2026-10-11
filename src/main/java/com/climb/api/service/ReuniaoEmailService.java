package com.climb.api.service;

import com.climb.api.model.Reuniao;
import com.climb.api.model.Usuario;
import org.springframework.stereotype.Service;

import java.time.format.DateTimeFormatter;
import java.util.List;

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
                + ". Horário: " + reuniao.getHora().format(DateTimeFormatter.ofPattern("HH:mm")) + ".\n"
                + (Boolean.TRUE.equals(reuniao.getPresencial()) ? "Local: " + reuniao.getLocal()
                : "Google Meet: " + (reuniao.getLinkMeet() == null ? "Disponível no convite do Google Agenda" : reuniao.getLinkMeet()));
        String destino = "/agenda?reuniaoId=" + reuniao.getIdReuniao();
        convidados.forEach(usuario -> entrega.notificar(usuario, "REUNIAO_AGENDADA", "Convite para reunião",
                mensagem, destino));
        if (convidados.stream().noneMatch(u -> GoogleCalendarService.EMAIL_OBRIGATORIO.equalsIgnoreCase(u.getEmail()))) {
            entrega.avisarPorEmail(GoogleCalendarService.EMAIL_OBRIGATORIO, "Convite para reunião", mensagem, destino);
        }
        reuniao.setConvitesNotificados(true);
    }
}
