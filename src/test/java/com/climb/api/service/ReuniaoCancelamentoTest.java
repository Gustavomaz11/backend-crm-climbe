package com.climb.api.service;

import com.climb.api.model.Reuniao;
import com.climb.api.model.dto.ReuniaoRequestDTO;
import com.climb.api.repository.EmpresaRepository;
import com.climb.api.repository.ParticipanteReuniaoRepository;
import com.climb.api.repository.ReuniaoRepository;
import com.climb.api.repository.UsuarioRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReuniaoCancelamentoTest {
    @Mock ReuniaoRepository repository;
    @Mock EmpresaRepository empresas;
    @Mock GoogleCalendarService calendar;
    @Mock ParticipanteReuniaoRepository participantes;
    @Mock UsuarioRepository usuarios;
    @Mock ReuniaoEmailService emails;
    @Mock GoogleCredentialService credentials;
    @Mock GoogleSalaService salas;
    @Mock ReuniaoDisponibilidadeService disponibilidade;
    @InjectMocks ReuniaoService service;

    @Test void criadorCancelaNoGoogleEPreservaReuniaoEParticipantesNoHistorico() throws Exception {
        var reuniao = reuniao();
        when(repository.findByIdForUpdate(3L)).thenReturn(Optional.of(reuniao));
        when(credentials.obterAccessToken(1L)).thenReturn(Optional.of("token-criador"));
        when(repository.save(reuniao)).thenReturn(reuniao);

        assertThat(service.cancelar(3L, 1L)).isSameAs(reuniao);

        assertThat(reuniao.getStatus()).isEqualTo("CANCELADA");
        assertThat(reuniao.getLinkMeet()).isNull();
        assertThat(reuniao.getGoogleEventId()).isEqualTo("evento");
        var ordem = inOrder(calendar, repository);
        ordem.verify(calendar).deletarEvento("evento", "token-criador");
        ordem.verify(repository).save(reuniao);
        verify(repository, never()).delete(any());
        verifyNoInteractions(participantes, emails);
    }

    @Test void cancelaReuniaoSemEventoGoogleSemExigirCredenciais() {
        var reuniao = reuniao(); reuniao.setGoogleEventId(null);
        when(repository.findByIdForUpdate(3L)).thenReturn(Optional.of(reuniao));
        when(repository.save(reuniao)).thenReturn(reuniao);

        assertThat(service.cancelar(3L, 1L).getStatus()).isEqualTo("CANCELADA");
        verifyNoInteractions(credentials, calendar, participantes);
    }

    @Test void impedeCancelamentoPorConvidadoOuOutroUsuario() {
        when(repository.findByIdForUpdate(3L)).thenReturn(Optional.of(reuniao()));

        assertThatThrownBy(() -> service.cancelar(3L, 2L)).isInstanceOfSatisfying(ResponseStatusException.class,
                e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN));

        verify(repository, never()).save(any());
        verifyNoInteractions(credentials, calendar, participantes);
    }

    @Test void exigeUsuarioAutenticadoAntesDeConsultarReuniao() {
        assertThatThrownBy(() -> service.cancelar(3L, null)).isInstanceOfSatisfying(ResponseStatusException.class,
                e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED));
        verifyNoInteractions(repository, credentials, calendar);
    }

    @Test void informaQuandoReuniaoNaoExiste() {
        assertThatThrownBy(() -> service.cancelar(3L, 1L)).isInstanceOfSatisfying(ResponseStatusException.class,
                e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
        verifyNoInteractions(credentials, calendar);
    }

    @ParameterizedTest @ValueSource(strings = {"CANCELADA", "CANCELADO"})
    void repetirCancelamentoNaoEnviaNovaNotificacao(String status) {
        var reuniao = reuniao(); reuniao.setStatus(status);
        when(repository.findByIdForUpdate(3L)).thenReturn(Optional.of(reuniao));

        assertThat(service.cancelar(3L, 1L)).isSameAs(reuniao);
        verify(repository, never()).save(any());
        verifyNoInteractions(credentials, calendar, participantes);
    }

    @Test void falhaNoGooglePreservaAgendamentoELinkMeet() throws Exception {
        var reuniao = reuniao();
        when(repository.findByIdForUpdate(3L)).thenReturn(Optional.of(reuniao));
        when(credentials.obterAccessToken(1L)).thenReturn(Optional.of("token"));
        doThrow(new IOException("Google indisponível")).when(calendar).deletarEvento("evento", "token");

        assertThatThrownBy(() -> service.cancelar(3L, 1L)).isInstanceOfSatisfying(ResponseStatusException.class,
                e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY));

        assertThat(reuniao.getStatus()).isEqualTo("AGENDADA");
        assertThat(reuniao.getLinkMeet()).isNotBlank();
        verify(repository, never()).save(any());
        verifyNoInteractions(participantes);
    }

    @Test void ausenciaDeCredenciaisPreservaReuniao() {
        var reuniao = reuniao();
        when(repository.findByIdForUpdate(3L)).thenReturn(Optional.of(reuniao));
        assertThatThrownBy(() -> service.cancelar(3L, 1L)).isInstanceOfSatisfying(ResponseStatusException.class,
                e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
        assertThat(reuniao.getStatus()).isEqualTo("AGENDADA");
        verify(repository, never()).save(any());
        verifyNoInteractions(calendar);
    }

    @Test void atualizarStatusParaCanceladaTambemExigeCriador() {
        when(repository.findByIdForUpdate(3L)).thenReturn(Optional.of(reuniao()));
        var request = new ReuniaoRequestDTO(); request.setStatus("CANCELADA");
        assertThatThrownBy(() -> service.atualizar(3L, 2L, request)).isInstanceOfSatisfying(ResponseStatusException.class,
                e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN));
        verifyNoInteractions(credentials, calendar, participantes);
    }

    @Test void cancelamentoPeloStatusDoKanbanUsaMesmoFluxo() throws Exception {
        var reuniao = reuniao();
        when(repository.findByIdForUpdate(3L)).thenReturn(Optional.of(reuniao));
        when(credentials.obterAccessToken(1L)).thenReturn(Optional.of("token"));
        when(repository.save(reuniao)).thenReturn(reuniao);
        var request = new ReuniaoRequestDTO(); request.setStatus("CANCELADO");
        assertThat(service.atualizar(3L, 1L, request).getStatus()).isEqualTo("CANCELADA");
        verify(calendar).deletarEvento("evento", "token");
    }

    @Test void sincronizacaoGoogleMantemHistoricoDaReuniaoCancelada() throws Exception {
        var reuniao = reuniao(); reuniao.setStatus("CANCELADA");
        when(repository.findVisiveisParaUsuario(1L)).thenReturn(List.of(reuniao));
        when(credentials.obterAccessToken(1L)).thenReturn(Optional.of("token"));

        assertThat(service.listar(1L)).singleElement().satisfies(dto -> assertThat(dto.getStatus()).isEqualTo("CANCELADA"));

        verify(calendar, never()).eventoExiste(any(), any());
        verify(repository, never()).delete(any());
        verifyNoInteractions(participantes);
    }

    private Reuniao reuniao() {
        var reuniao = new Reuniao();
        reuniao.setIdReuniao(3L); reuniao.setOrganizadorId(1L); reuniao.setTitulo("Reunião da equipe");
        reuniao.setStatus("AGENDADA"); reuniao.setGoogleEventId("evento");
        reuniao.setLinkMeet("https://meet.google.com/abc-defg-hij");
        return reuniao;
    }
}
