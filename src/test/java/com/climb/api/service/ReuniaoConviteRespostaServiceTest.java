package com.climb.api.service;

import com.climb.api.model.*;
import com.climb.api.model.enums.RespostaConviteReuniao;
import com.climb.api.repository.ParticipanteReuniaoRepository;
import com.google.api.services.calendar.model.Event;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.web.server.ResponseStatusException;
import java.time.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReuniaoConviteRespostaServiceTest {
    @Mock ParticipanteReuniaoRepository participantes;
    @Mock GoogleCredentialService credentials;
    @Mock GoogleCalendarService calendar;
    @Mock PlatformTransactionManager manager;
    @InjectMocks ReuniaoConviteRespostaService service;

    @Test void aceitaSomenteOConviteDoUsuarioAutenticado() {
        var convite = convite();
        when(participantes.findConviteForUpdate(3L, 2L)).thenReturn(Optional.of(convite));
        var resposta = service.aceitar(3L, 2L);
        assertThat(resposta.getMinhaResposta()).isEqualTo(RespostaConviteReuniao.ACEITA);
        assertThat(resposta.getRespondidoEm()).isNotNull();
        assertThat(resposta.getConviteId()).isEqualTo(8L);
        assertThat(convite.isAceiteSincronizadoGoogle()).isFalse();
        verify(participantes).save(convite);
    }

    @Test void naoPermiteAceitarConviteDeOutraPessoa() {
        assertThatThrownBy(() -> service.aceitar(3L, 99L)).isInstanceOf(ResponseStatusException.class).hasMessageContaining("403");
        verify(participantes, never()).save(any());
        verifyNoInteractions(calendar, credentials);
    }

    @Test void confirmaMesmoSeGoogleFalharEDeixaSincronizacaoPendente() throws Exception {
        var convite = convite();
        when(participantes.findConviteForUpdate(3L, 2L)).thenReturn(Optional.of(convite));
        when(credentials.obterAccessToken(2L)).thenReturn(Optional.of("token"));
        when(calendar.buscarEvento("evento", "token")).thenThrow(new java.io.IOException("offline"));
        assertThat(service.aceitar(3L, 2L).getMinhaResposta()).isEqualTo(RespostaConviteReuniao.ACEITA);
        assertThat(convite.isAceiteSincronizadoGoogle()).isFalse();
        verify(participantes).save(convite);
    }

    @Test void sincronizaAceiteNoGoogleSemConfirmarOutrosConvidados() throws Exception {
        var convite = convite(); var evento = new Event().setId("evento");
        when(participantes.findConviteForUpdate(3L, 2L)).thenReturn(Optional.of(convite));
        when(credentials.obterAccessToken(2L)).thenReturn(Optional.of("token"));
        when(calendar.buscarEvento("evento", "token")).thenReturn(evento);
        service.aceitar(3L, 2L);
        assertThat(convite.isAceiteSincronizadoGoogle()).isTrue();
        verify(calendar).aceitarConvite(evento, "ana@example.test", "token");
    }

    @Test void repetirAceiteMantemDataENaoRepeteAtualizacaoGoogle() {
        var convite = convite(); var respondido = LocalDateTime.now().minusMinutes(1);
        convite.setResposta(RespostaConviteReuniao.ACEITA); convite.setRespondidoEm(respondido); convite.setAceiteSincronizadoGoogle(true);
        when(participantes.findConviteForUpdate(3L, 2L)).thenReturn(Optional.of(convite));
        assertThat(service.aceitar(3L, 2L).getRespondidoEm()).isEqualTo(respondido);
        verifyNoInteractions(calendar, credentials);
    }

    @Test void encontraCopiaDoConvitePeloUidQuandoIdDaAgendaDoConvidadoDifere() throws Exception {
        var convite = convite(); convite.getReuniao().setOrganizadorId(1L);
        when(participantes.findConviteForUpdate(3L, 2L)).thenReturn(Optional.of(convite));
        when(credentials.obterAccessToken(2L)).thenReturn(Optional.of("token-ana"));
        when(credentials.obterAccessToken(1L)).thenReturn(Optional.of("token-organizador"));
        var erro = new com.google.api.client.googleapis.json.GoogleJsonResponseException(
                new com.google.api.client.http.HttpResponseException.Builder(404, "Not found", new com.google.api.client.http.HttpHeaders()),
                new com.google.api.client.googleapis.json.GoogleJsonError());
        when(calendar.buscarEvento("evento", "token-ana")).thenThrow(erro);
        when(calendar.buscarEvento("evento", "token-organizador")).thenReturn(new Event().setId("evento").setICalUID("uid"));
        var copia = new Event().setId("copia"); when(calendar.buscarEventoPorICalUID("uid", "token-ana")).thenReturn(copia);
        service.aceitar(3L, 2L);
        verify(calendar).aceitarConvite(copia, "ana@example.test", "token-ana");
        assertThat(convite.isAceiteSincronizadoGoogle()).isTrue();
    }

    @Test void naoAceitaReuniaoCanceladaOuJaEncerrada() {
        var convite = convite(); convite.getReuniao().setStatus("CANCELADA");
        when(participantes.findConviteForUpdate(3L, 2L)).thenReturn(Optional.of(convite));
        assertThatThrownBy(() -> service.aceitar(3L, 2L)).isInstanceOf(ResponseStatusException.class).hasMessageContaining("cancelada");
        convite.getReuniao().setStatus("AGENDADA"); convite.getReuniao().setDataHoraFim(LocalDateTime.now().minusDays(1));
        assertThatThrownBy(() -> service.aceitar(3L, 2L)).isInstanceOf(ResponseStatusException.class).hasMessageContaining("terminou");
        verify(participantes, never()).save(any());
    }

    @Test void popupRecebeConvitePendenteMesmoSemMeetProntoEMasNaoReuniaoEncerrada() {
        var convite = convite(); convite.getReuniao().setLinkMeet(null);
        var passada = convite(); passada.getReuniao().setDataHoraFim(LocalDateTime.now().minusDays(1));
        when(participantes.findConvitesPendentes(eq(2L), any())).thenReturn(List.of(convite, passada));
        var convites = service.listarPendentes(2L);
        assertThat(convites).hasSize(1);
        assertThat(convites.getFirst().getMinhaResposta()).isEqualTo(RespostaConviteReuniao.PENDENTE);
        verifyNoInteractions(calendar, credentials);
    }

    @Test void processadorRetomaSincronizacaoPendente() throws Exception {
        var convite = convite(); convite.setResposta(RespostaConviteReuniao.ACEITA);
        when(participantes.findIdsAceitesPendentesGoogle(any())).thenReturn(List.of(8L));
        when(participantes.findByIdForUpdate(8L)).thenReturn(Optional.of(convite));
        when(credentials.obterAccessToken(2L)).thenReturn(Optional.of("token"));
        var evento = new Event().setId("evento"); when(calendar.buscarEvento("evento", "token")).thenReturn(evento);
        service.sincronizarPendentesGoogle();
        assertThat(convite.isAceiteSincronizadoGoogle()).isTrue();
        verify(participantes).save(convite);
    }

    private ParticipanteReuniao convite() {
        var usuario = new Usuario(); usuario.setId(2L); usuario.setEmail("ana@example.test");
        var reuniao = new Reuniao(); reuniao.setIdReuniao(3L); reuniao.setGoogleEventId("evento");
        reuniao.setTitulo("Reunião da equipe"); reuniao.setData(LocalDate.now().plusDays(1)); reuniao.setHora(LocalTime.of(10, 0));
        var convite = new ParticipanteReuniao(); convite.setId(8L); convite.setUsuario(usuario); convite.setReuniao(reuniao); return convite;
    }
}
