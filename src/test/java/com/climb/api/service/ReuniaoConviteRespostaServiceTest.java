package com.climb.api.service;

import com.climb.api.model.*;
import com.climb.api.model.enums.RespostaConviteReuniao;
import com.climb.api.repository.ParticipanteReuniaoRepository;
import com.climb.api.repository.UsuarioRepository;
import com.google.api.services.calendar.model.Event;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
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
    @Mock UsuarioRepository usuarios;
    @Mock NotificacaoService notificacoes;
    @Mock org.springframework.context.ApplicationEventPublisher eventos;
    ReuniaoConviteRespostaService service;

    @BeforeEach void configurar() {
        service = new ReuniaoConviteRespostaService(participantes, credentials, calendar, manager,
                new ReuniaoRespostaRegistroService(usuarios, notificacoes, eventos));
    }

    @Test void avisaSomenteOrganizadorUmaVezPorRespostaENaoRepeteNoReenvio() {
        var convite = convite();
        var organizador = new Usuario(); organizador.setId(1L);
        when(usuarios.findById(1L)).thenReturn(Optional.of(organizador));
        when(participantes.findConviteForUpdate(3L, 2L)).thenReturn(Optional.of(convite));
        service.aceitar(3L, 2L);
        service.aceitar(3L, 2L);
        service.rejeitar(3L, 2L);
        service.rejeitar(3L, 2L);
        var avisos = ArgumentCaptor.forClass(Notificacao.class);
        verify(notificacoes, times(2)).criar(avisos.capture());
        assertThat(avisos.getAllValues()).allSatisfy(n -> {
            assertThat(n.getUsuario().getId()).isEqualTo(1L);
            assertThat(n.getDestino()).isEqualTo("/agenda?reuniaoId=3");
        });
        assertThat(avisos.getAllValues()).extracting(Notificacao::getTipo).containsExactly("REUNIAO_ACEITA", "REUNIAO_RECUSADA");
        assertThat(avisos.getAllValues().get(0).getMensagem()).contains("ana@example.test confirmou presença", "Reunião da equipe");
        assertThat(avisos.getAllValues().get(1).getMensagem()).contains("recusou o convite");
        verify(eventos, times(2)).publishEvent(new AvisoAtualizado(null, 3L, AvisoAtualizado.Tipo.CONVITES));
    }

    @Test void recusaAtualizaApenasConviteAutenticadoESincronizaComGoogle() throws Exception {
        var convite = convite(); var evento = new Event().setId("evento");
        when(participantes.findConviteForUpdate(3L, 2L)).thenReturn(Optional.of(convite));
        when(credentials.obterAccessToken(2L)).thenReturn(Optional.of("token"));
        when(calendar.buscarEvento("evento", "token")).thenReturn(evento);
        assertThat(service.rejeitar(3L, 2L).getMinhaResposta()).isEqualTo(RespostaConviteReuniao.RECUSADA);
        assertThat(convite.getRespondidoEm()).isNotNull();
        assertThat(convite.isAceiteSincronizadoGoogle()).isTrue();
        verify(calendar).rejeitarConvite(evento, "ana@example.test", "token");
    }

    @Test void naoPermiteRecusarConviteAlheioNemOrganizadorRecusarPropriaReuniao() {
        assertThatThrownBy(() -> service.rejeitar(3L, 99L)).isInstanceOf(ResponseStatusException.class).hasMessageContaining("403");
        var convite = convite(); convite.getReuniao().setOrganizadorId(2L);
        when(participantes.findConviteForUpdate(3L, 2L)).thenReturn(Optional.of(convite));
        assertThatThrownBy(() -> service.rejeitar(3L, 2L)).isInstanceOf(ResponseStatusException.class).hasMessageContaining("organizador");
        verifyNoInteractions(notificacoes, calendar, credentials);
        verify(participantes, never()).save(any());
    }

    @Test void aceiteAutomaticoDoOrganizadorNaoGeraAvisoParaSiMesmo() {
        var convite = convite(); convite.getReuniao().setOrganizadorId(2L);
        when(participantes.findConviteForUpdate(3L, 2L)).thenReturn(Optional.of(convite));
        assertThat(service.aceitar(3L, 2L).getSouOrganizador()).isTrue();
        assertThat(convite.getResposta()).isEqualTo(RespostaConviteReuniao.ACEITA);
        verifyNoInteractions(notificacoes, credentials, calendar);
    }

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
        var reuniao = new Reuniao(); reuniao.setIdReuniao(3L); reuniao.setOrganizadorId(1L); reuniao.setGoogleEventId("evento");
        reuniao.setTitulo("Reunião da equipe"); reuniao.setData(LocalDate.now().plusDays(1)); reuniao.setHora(LocalTime.of(10, 0));
        var convite = new ParticipanteReuniao(); convite.setId(8L); convite.setUsuario(usuario); convite.setReuniao(reuniao); return convite;
    }
}
