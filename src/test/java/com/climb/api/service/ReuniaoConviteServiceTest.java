package com.climb.api.service;

import com.climb.api.model.*;
import com.climb.api.repository.*;
import com.google.api.services.calendar.model.Event;
import org.junit.jupiter.api.Test;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

class ReuniaoConviteServiceTest {
    @Test void retentaMeetPendenteEEnviaUmaUnicaVezQuandoPronto() throws Exception {
        var repository = mock(ReuniaoRepository.class); var participantes = mock(ParticipanteReuniaoRepository.class);
        var credentials = mock(GoogleCredentialService.class); var google = mock(GoogleCalendarService.class);
        var entrega = mock(NotificacaoEntregaService.class); var avisos = new ReuniaoEmailService(entrega);
        var manager = new NotificacaoEntregaServiceTest.Config().transactionManager();
        var service = new ReuniaoConviteService(repository, participantes, credentials, google, avisos, manager);
        Reuniao reuniao = new Reuniao(); reuniao.setIdReuniao(1L); reuniao.setOrganizadorId(2L);
        reuniao.setGoogleEventId("evento"); reuniao.setPresencial(false); reuniao.setTitulo("Online"); reuniao.setData(LocalDate.of(2026,11,15)); reuniao.setHora(LocalTime.NOON);
        when(repository.findIdsConvitesPendentes()).thenReturn(List.of(1L)); when(repository.findByIdForUpdate(1L)).thenReturn(Optional.of(reuniao));
        when(credentials.obterAccessToken(2L)).thenReturn(Optional.of("token-teste"));
        Event evento = new Event(); when(google.buscarEvento("evento", "token-teste")).thenReturn(evento);
        when(google.extrairLinkMeet(evento)).thenReturn(null, "https://meet.google.com/abc-defg-hij");
        Usuario convidado = new Usuario(); convidado.setId(3L); convidado.setEmail("ana@example.test");
        ParticipanteReuniao participante = new ParticipanteReuniao(); participante.setUsuario(convidado);
        Usuario organizador = new Usuario(); organizador.setId(2L); organizador.setEmail("organizador@example.test");
        ParticipanteReuniao participanteOrganizador = new ParticipanteReuniao(); participanteOrganizador.setUsuario(organizador);
        when(participantes.findByReuniao_IdReuniao(1L)).thenReturn(List.of(participanteOrganizador, participante));
        service.processarPendentes(); assertFalse(reuniao.isConvitesNotificados()); verifyNoInteractions(entrega);
        service.processarPendentes(); assertTrue(reuniao.isConvitesNotificados()); assertEquals("https://meet.google.com/abc-defg-hij", reuniao.getLinkMeet());
        verify(entrega).notificar(eq(convidado), eq("REUNIAO_AGENDADA"), anyString(), contains("https://meet.google.com/abc-defg-hij"), eq("/agenda?reuniaoId=1"));
        verify(entrega).avisarPorEmail(eq(GoogleCalendarService.EMAIL_OBRIGATORIO), anyString(), anyString(), anyString());
        verifyNoMoreInteractions(entrega);
        clearInvocations(entrega); service.processarPendentes(); verifyNoInteractions(entrega);
    }
}
