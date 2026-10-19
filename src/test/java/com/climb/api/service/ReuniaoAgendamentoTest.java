package com.climb.api.service;

import com.climb.api.model.*;
import com.climb.api.model.dto.ReuniaoRequestDTO;
import com.climb.api.model.dto.SalaReuniaoDTO;
import com.climb.api.repository.*;
import com.google.api.services.calendar.model.Event;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReuniaoAgendamentoTest {
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
    private static final String SALA = "sala@resource.calendar.google.com";

    @Test void detalhesDeReuniaoLocalUsamSalaFisicaVinculadaAoEventoGoogle() throws Exception {
        String nome = "Escritório Climbe Cont - Hermes Fontes-2-Sala de Reunião - Climbe Cont (6)";
        var reuniao = new Reuniao(); reuniao.setIdReuniao(3L); reuniao.setGoogleEventId("evento");
        reuniao.setLocal("Local antigo");
        when(repository.findById(3L)).thenReturn(Optional.of(reuniao));
        when(participantes.existsByReuniao_IdReuniaoAndUsuario_Id(3L, 2L)).thenReturn(true);
        when(credentials.obterAccessToken(2L)).thenReturn(Optional.of("token"));
        when(calendar.buscarEvento("evento", "token")).thenReturn(new Event().setAttendees(List.of(
                new com.google.api.services.calendar.model.EventAttendee().setEmail(SALA).setResource(true).setDisplayName(nome))));
        var detalhes = service.buscarDetalhes(3L, 2L);
        assertThat(detalhes.getLocal()).isEqualTo(nome);
        assertThat(detalhes.getSalaEmail()).isEqualTo(SALA);
        assertThat(reuniao.getLocal()).isEqualTo("Local antigo");
    }

    @Test void consultaDetalhesComParticipantesEOrganizadorSemExigirGoogle() {
        var reuniao = new Reuniao(); reuniao.setIdReuniao(3L); reuniao.setOrganizadorId(1L);
        reuniao.setTitulo("Reunião da equipe"); reuniao.setPauta("Revisar entregas"); reuniao.setLocal("Sala 1");
        reuniao.setData(LocalDate.of(2026, 10, 19)); reuniao.setHora(LocalTime.of(10, 0));
        var organizador = usuario(1L); organizador.setNomeCompleto("Ana");
        var convidado = usuario(2L); convidado.setNomeCompleto("Bruno");
        var participante = new ParticipanteReuniao(); participante.setUsuario(convidado);
        when(repository.findById(3L)).thenReturn(Optional.of(reuniao));
        when(participantes.existsByReuniao_IdReuniaoAndUsuario_Id(3L, 2L)).thenReturn(true);
        when(participantes.findByReuniao_IdReuniao(3L)).thenReturn(List.of(participante));
        when(usuarios.findById(1L)).thenReturn(Optional.of(organizador));

        var detalhes = service.buscarDetalhes(3L, 2L);

        assertThat(detalhes.getEmpresa()).isNull();
        assertThat(detalhes.getPauta()).isEqualTo("Revisar entregas");
        assertThat(detalhes.getLocal()).isEqualTo("Sala 1");
        assertThat(detalhes.getOrganizador().nomeCompleto()).isEqualTo("Ana");
        assertThat(detalhes.getParticipantes()).extracting(pessoa -> pessoa.nomeCompleto()).containsExactly("Bruno");
        assertThat(detalhes.getDataHoraFim()).isEqualTo("2026-10-19T11:00:00-03:00");
        verifyNoInteractions(calendar, credentials);
    }

    @Test void naoExpoeDetalhesOuParticipantesParaUsuarioForaDaReuniao() {
        var reuniao = new Reuniao(); reuniao.setIdReuniao(3L);
        when(repository.findById(3L)).thenReturn(Optional.of(reuniao));
        assertThatThrownBy(() -> service.buscarDetalhes(3L, 99L)).isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("403");
        verify(participantes, never()).findByReuniao_IdReuniao(any());
        verifyNoInteractions(usuarios, calendar);
    }

    @Test void agendaEquipeSemClienteComParticipantesESalaDoGoogle() throws Exception {
        var request = request();
        request.setSalaEmail(SALA);
        request.setParticipanteIds(List.of(2L));
        request.setHoraFim(LocalTime.of(12, 30));
        when(credentials.obterAccessToken(1L)).thenReturn(Optional.of("token"));
        when(salas.listar("token")).thenReturn(List.of(new SalaReuniaoDTO(SALA, "Sala 1", 6)));
        when(repository.save(any())).thenAnswer(call -> { Reuniao r = call.getArgument(0); r.setIdReuniao(3L); return r; });
        when(usuarios.findAllById(any())).thenReturn(List.of(usuario(1L), usuario(2L)));
        when(calendar.criarEvento(any(), eq("token"), any())).thenReturn(new Event().setId("evento"));

        var reuniao = service.criar(request, 1L);

        assertThat(reuniao.getEmpresa()).isNull();
        assertThat(reuniao.getSalaEmail()).isEqualTo(SALA);
        assertThat(reuniao.getLocal()).isEqualTo("Sala 1");
        assertThat(reuniao.getGoogleEventId()).isEqualTo("evento");
        assertThat(reuniao.getDataHoraFim()).isEqualTo(request.getData().atTime(12, 30));
        verify(disponibilidade).exigirLocalDisponivel(eq(reuniao), eq("token"));
        verify(calendar).criarEvento(eq(reuniao), eq("token"), eq(List.of("1@example.test", "2@example.test")));
        verifyNoInteractions(empresas);
        verify(emails).notificarAgendamento(eq(reuniao), argThat(lista -> lista.size() == 2));
    }

    @Test void rejeitaSalaQueNaoPertenceAoCatalogoAntesDeSalvar() {
        var request = request(); request.setSalaEmail(SALA);
        when(credentials.obterAccessToken(1L)).thenReturn(Optional.of("token"));
        when(salas.listar("token")).thenReturn(List.of());
        assertThatThrownBy(() -> service.criar(request, 1L)).isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("sala selecionada");
        verifyNoInteractions(repository, calendar, emails);
    }

    @Test void naoSalvaNemEnviaConviteSeSalaFicouOcupadaAntesDaConfirmacao() {
        var request = request();
        when(credentials.obterAccessToken(1L)).thenReturn(Optional.of("token"));
        doThrow(new ResponseStatusException(org.springframework.http.HttpStatus.CONFLICT, "Sala ocupada"))
                .when(disponibilidade).exigirLocalDisponivel(any(), eq("token"));
        assertThatThrownBy(() -> service.criar(request, 1L)).isInstanceOf(ResponseStatusException.class).hasMessageContaining("409");
        verifyNoInteractions(repository, calendar, emails);
    }

    @Test void permiteRemoverClienteEPreservaParticipantesESalaAoAlterarStatus() throws Exception {
        var reuniao = new Reuniao(); reuniao.setIdReuniao(3L); reuniao.setOrganizadorId(1L);
        reuniao.setData(LocalDate.of(2026, 10, 20)); reuniao.setHora(LocalTime.of(10, 0));
        reuniao.setDataHoraFim(reuniao.getData().atTime(12, 30));
        reuniao.setPresencial(true); reuniao.setStatus("AGENDADA");
        reuniao.setEmpresa(new Empresa()); reuniao.setSalaEmail(SALA); reuniao.setLocal("Sala 1"); reuniao.setGoogleEventId("evento");
        var participante = new ParticipanteReuniao(); participante.setUsuario(usuario(2L));
        when(repository.findById(3L)).thenReturn(Optional.of(reuniao));
        when(participantes.existsByReuniao_IdReuniaoAndUsuario_Id(3L, 1L)).thenReturn(true);
        when(participantes.findByReuniao_IdReuniao(3L)).thenReturn(List.of(participante));
        when(credentials.obterAccessToken(1L)).thenReturn(Optional.of("token"));
        when(repository.save(any())).thenAnswer(call -> call.getArgument(0));
        when(usuarios.findAllById(any())).thenReturn(List.of(usuario(1L), usuario(2L)));
        when(calendar.atualizarEvento(any(), eq("token"), any())).thenReturn(new Event().setId("evento"));
        var request = request(); request.setSalaEmail(SALA); request.setStatus("CONFIRMADA"); request.setLocal("Sala 1");

        var atualizada = service.atualizar(3L, 1L, request);

        assertThat(atualizada.getEmpresa()).isNull();
        assertThat(atualizada.getSalaEmail()).isEqualTo(SALA);
        assertThat(atualizada.getStatus()).isEqualTo("CONFIRMADA");
        assertThat(atualizada.getDataHoraFim()).isEqualTo(reuniao.getData().atTime(12, 30));
        verify(usuarios).findAllById(argThat(ids -> { var lista = new java.util.ArrayList<Long>(); ids.forEach(lista::add); return lista.containsAll(List.of(1L, 2L)); }));
        verifyNoInteractions(empresas, salas, disponibilidade);
    }

    private ReuniaoRequestDTO request() {
        var request = new ReuniaoRequestDTO(); request.setTitulo("Alinhamento da equipe");
        request.setData(LocalDate.of(2026, 10, 20)); request.setHora(LocalTime.of(10, 0));
        request.setPresencial(true); request.setLocal("Local informado");
        return request;
    }

    private Usuario usuario(Long id) {
        var usuario = new Usuario(); usuario.setId(id); usuario.setEmail(id + "@example.test"); return usuario;
    }
}
