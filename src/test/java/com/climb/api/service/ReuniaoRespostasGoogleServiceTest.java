package com.climb.api.service;

import com.climb.api.model.*;
import com.climb.api.model.enums.RespostaConviteReuniao;
import com.climb.api.repository.*;
import com.google.api.services.calendar.model.Event;
import com.google.api.services.calendar.model.EventAttendee;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.PlatformTransactionManager;
import java.time.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReuniaoRespostasGoogleServiceTest {
    @Mock ReuniaoRepository reunioes;
    @Mock ParticipanteReuniaoRepository participantes;
    @Mock UsuarioRepository usuarios;
    @Mock GoogleCredentialService credentials;
    @Mock GoogleCalendarService calendar;
    @Mock NotificacaoService notificacoes;
    @Mock ApplicationEventPublisher eventos;
    @Mock PlatformTransactionManager manager;
    ReuniaoRespostasGoogleService service;
    Reuniao reuniao;
    ParticipanteReuniao convidado;
    Usuario organizador;

    @BeforeEach void configurar() {
        service = new ReuniaoRespostasGoogleService(reunioes, participantes, credentials, calendar,
                new ReuniaoRespostaRegistroService(usuarios, notificacoes, eventos), manager);
        organizador = new Usuario(); organizador.setId(1L); organizador.setEmail("org@climbe.com.br");
        var usuario = new Usuario(); usuario.setId(2L); usuario.setNomeCompleto("Ana"); usuario.setEmail("ana@climbe.com.br");
        reuniao = new Reuniao(); reuniao.setIdReuniao(3L); reuniao.setOrganizadorId(1L); reuniao.setGoogleEventId("evento");
        reuniao.setTitulo("Revisão da equipe"); reuniao.setData(LocalDate.now().plusDays(1)); reuniao.setHora(LocalTime.NOON);
        convidado = new ParticipanteReuniao(); convidado.setId(8L); convidado.setReuniao(reuniao); convidado.setUsuario(usuario);
        when(reunioes.findIdsRespostasGoogle(any())).thenReturn(List.of(3L));
        when(reunioes.findById(3L)).thenReturn(Optional.of(reuniao));
        when(credentials.obterAccessToken(1L)).thenReturn(Optional.of("token-organizador"));
    }

    @Test void respostasNoGoogleNotificamOrganizadorSemDuplicarEAtualizamStatus() throws Exception {
        when(participantes.findByReuniao_IdReuniao(3L)).thenReturn(List.of(convidado));
        when(participantes.findByIdForUpdate(8L)).thenReturn(Optional.of(convidado));
        when(usuarios.findById(1L)).thenReturn(Optional.of(organizador));
        when(calendar.buscarEvento("evento", "token-organizador")).thenReturn(evento("accepted"));
        service.sincronizar(); service.sincronizar();
        assertThat(convidado.getResposta()).isEqualTo(RespostaConviteReuniao.ACEITA);
        assertThat(convidado.isAceiteSincronizadoGoogle()).isTrue();
        when(calendar.buscarEvento("evento", "token-organizador")).thenReturn(evento("declined"));
        service.sincronizar(); service.sincronizar();
        assertThat(convidado.getResposta()).isEqualTo(RespostaConviteReuniao.RECUSADA);
        var avisos = ArgumentCaptor.forClass(Notificacao.class);
        verify(notificacoes, times(2)).criar(avisos.capture());
        assertThat(avisos.getAllValues()).allSatisfy(n -> assertThat(n.getUsuario()).isSameAs(organizador));
        assertThat(avisos.getAllValues()).extracting(Notificacao::getTipo).containsExactly("REUNIAO_ACEITA", "REUNIAO_RECUSADA");
        verify(calendar, never()).rejeitarConvite(any(), any(), any());
        verify(calendar, never()).aceitarConvite(any(), any(), any());
    }

    @Test void preservaDecisaoLocalAindaNaoEnviadaAoGoogle() throws Exception {
        convidado.setResposta(RespostaConviteReuniao.RECUSADA);
        when(participantes.findByReuniao_IdReuniao(3L)).thenReturn(List.of(convidado));
        when(participantes.findByIdForUpdate(8L)).thenReturn(Optional.of(convidado));
        when(calendar.buscarEvento("evento", "token-organizador")).thenReturn(evento("accepted"));
        service.sincronizar();
        assertThat(convidado.getResposta()).isEqualTo(RespostaConviteReuniao.RECUSADA);
        assertThat(convidado.isAceiteSincronizadoGoogle()).isFalse();
        verifyNoInteractions(notificacoes);
    }

    @Test void convidadosAusentesOuSemRespostaNaoSaoTratadosComoRecusa() throws Exception {
        when(participantes.findByReuniao_IdReuniao(3L)).thenReturn(List.of(convidado));
        when(calendar.buscarEvento("evento", "token-organizador")).thenReturn(
                new Event().setAttendees(List.of()).setAttendeesOmitted(true), evento("needsAction"));
        service.sincronizar(); service.sincronizar();
        assertThat(convidado.getResposta()).isEqualTo(RespostaConviteReuniao.PENDENTE);
        verifyNoInteractions(notificacoes);
        verify(participantes, never()).findByIdForUpdate(any());
    }

    @Test void falhaOuCancelamentoDoGoogleNaoAlteraParticipacaoNemNotifica() throws Exception {
        when(calendar.buscarEvento("evento", "token-organizador")).thenThrow(new java.io.IOException("offline"))
                .thenReturn(evento("declined").setStatus("cancelled"));
        service.sincronizar(); service.sincronizar();
        assertThat(convidado.getResposta()).isEqualTo(RespostaConviteReuniao.PENDENTE);
        verifyNoInteractions(notificacoes, participantes);
    }

    private Event evento(String resposta) {
        return new Event().setId("evento").setAttendees(List.of(
                new EventAttendee().setEmail("ANA@CLIMBE.COM.BR").setResponseStatus(resposta)));
    }
}
