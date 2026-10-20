package com.climb.api.service;

import com.climb.api.model.Reuniao;
import com.climb.api.model.Usuario;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class ReuniaoEmailServiceTest {
    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void notificaSomenteConvidadosMesmoQuandoOrganizadorEstaEntreParticipantes(boolean presencial) {
        var entrega = mock(NotificacaoEntregaService.class);
        Reuniao reuniao = reuniao(); reuniao.setPresencial(presencial); reuniao.setLocal("Sala 2");
        reuniao.setOrganizadorId(1L); reuniao.setLinkMeet("https://meet.google.com/abc-defg-hij");
        Usuario organizador = new Usuario(); organizador.setId(1L); organizador.setEmail("organizador@example.test");
        Usuario convidado = new Usuario(); convidado.setId(2L); convidado.setEmail("convidado@example.test");

        new ReuniaoEmailService(entrega).notificarAgendamento(reuniao, List.of(organizador, convidado));

        verify(entrega).notificar(eq(convidado), eq("REUNIAO_AGENDADA"), anyString(), anyString(), eq("/agenda?reuniaoId=3"));
        verify(entrega).avisarPorEmail(eq(GoogleCalendarService.EMAIL_OBRIGATORIO), anyString(), anyString(), anyString());
        verifyNoMoreInteractions(entrega);
        org.junit.jupiter.api.Assertions.assertTrue(reuniao.isConvitesNotificados());
    }
    @Test void organizadorDaContaContatoNaoRecebeConviteDuplicadoPorEmail() {
        var entrega = mock(NotificacaoEntregaService.class);
        Reuniao reuniao = reuniao(); reuniao.setPresencial(true); reuniao.setLocal("Sala 2"); reuniao.setOrganizadorId(1L);
        Usuario organizador = new Usuario(); organizador.setId(1L); organizador.setEmail(GoogleCalendarService.EMAIL_OBRIGATORIO);
        Usuario convidado = new Usuario(); convidado.setId(2L); convidado.setEmail("convidado@example.test");

        new ReuniaoEmailService(entrega).notificarAgendamento(reuniao, List.of(organizador, convidado));

        verify(entrega).notificar(eq(convidado), eq("REUNIAO_AGENDADA"), anyString(), anyString(), anyString());
        verifyNoMoreInteractions(entrega);
    }
    @Test void aguardaMeetParaNaoEnviarConviteSemLink() {
        var entrega = mock(NotificacaoEntregaService.class);
        Reuniao reuniao = reuniao(); reuniao.setPresencial(false);
        new ReuniaoEmailService(entrega).notificarAgendamento(reuniao, List.of());
        verifyNoInteractions(entrega);
        org.junit.jupiter.api.Assertions.assertFalse(reuniao.isConvitesNotificados());
    }
    @Test void avisaConvidadosComDataHoraELocalSemDependerDeMeet() {
        var entrega = mock(NotificacaoEntregaService.class);
        Reuniao reuniao = reuniao(); reuniao.setPresencial(true); reuniao.setLocal("Sala 2");
        Usuario convidado = new Usuario(); convidado.setId(1L); convidado.setEmail("pessoa@example.test");
        new ReuniaoEmailService(entrega).notificarAgendamento(reuniao, List.of(convidado));
        verify(entrega).notificar(eq(convidado), eq("REUNIAO_AGENDADA"), anyString(), argThat(m -> m.contains("15/11/2026") && m.contains("14:30") && m.contains("Sala 2")), eq("/agenda?reuniaoId=3"));
        verify(entrega).avisarPorEmail(eq("contato@climbe.com.br"), anyString(), anyString(), anyString());
    }
    @Test void enviaLinkMeetENaoDuplicaConviteAoContatoCadastrado() {
        var entrega = mock(NotificacaoEntregaService.class);
        Reuniao reuniao = reuniao(); reuniao.setPresencial(false); reuniao.setLinkMeet("https://meet.google.com/abc-defg-hij");
        Usuario contato = new Usuario(); contato.setId(2L); contato.setEmail("contato@climbe.com.br");
        new ReuniaoEmailService(entrega).notificarAgendamento(reuniao, List.of(contato));
        verify(entrega).notificar(eq(contato), anyString(), anyString(), contains("https://meet.google.com/abc-defg-hij"), anyString());
        verifyNoMoreInteractions(entrega);
    }
    private Reuniao reuniao() { Reuniao r = new Reuniao(); r.setIdReuniao(3L); r.setTitulo("Planejamento"); r.setData(LocalDate.of(2026,11,15)); r.setHora(LocalTime.of(14,30)); return r; }
}
