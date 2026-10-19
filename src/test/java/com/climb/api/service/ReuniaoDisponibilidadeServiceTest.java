package com.climb.api.service;

import com.climb.api.model.*;
import com.climb.api.model.dto.ReuniaoDisponibilidadeRequestDTO;
import com.climb.api.repository.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;
import java.time.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReuniaoDisponibilidadeServiceTest {
    @Mock ReuniaoRepository reunioes;
    @Mock ParticipanteReuniaoRepository participantes;
    @Mock UsuarioRepository usuarios;
    @Mock GoogleDisponibilidadeService google;
    @InjectMocks ReuniaoDisponibilidadeService service;
    static final LocalDate DATA = LocalDate.of(2026, 10, 20);
    static final String SALA = "sala@resource.calendar.google.com";

    @ParameterizedTest
    @CsvSource({"09:00,10:00,true", "12:00,13:00,true", "09:30,10:30,false", "11:30,12:30,false",
            "10:30,11:00,false", "09:00,13:00,false", "10:00,12:00,false"})
    void verificaTodoIntervaloSemBloquearHorariosAdjacentes(String inicio, String fim, boolean livre) {
        var existente = reuniao(inicio, fim);
        preparar(List.of(existente));
        var resultado = service.consultar(request(null), null, "token");
        assertThat(resultado.salas().getFirst().disponivel()).isEqualTo(livre);
        assertThat(resultado.localDisponivel()).isEqualTo(livre);
    }

    @Test void mostraParticipanteOcupadoMesmoEmReuniaoQueNaoEstaVisivelAoSolicitante() {
        var existente = reuniao("11:00", "12:30"); existente.setSalaEmail("outra@resource.calendar.google.com");
        preparar(List.of(existente));
        var participante = new ParticipanteReuniao(); participante.setReuniao(existente); participante.setUsuario(usuario());
        when(participantes.findByReuniaoIn(any())).thenReturn(List.of(participante));
        assertThat(service.consultar(request(null), null, "token").participantes().getFirst().disponivel()).isFalse();
    }

    @Test void ignoraPropriaReuniaoECanceladasNaEdicao() {
        var propria = reuniao("10:00", "12:00");
        var cancelada = reuniao("10:00", "12:00"); cancelada.setIdReuniao(4L); cancelada.setStatus("CANCELADA");
        preparar(List.of(propria, cancelada));
        assertThat(service.consultar(request(3L), propria, "token").salas().getFirst().disponivel()).isTrue();
    }

    @Test void indisponibilidadeGoogleTambemBloqueiaSalaEParticipante() {
        preparar(List.of());
        when(google.consultar(any(), any(), any(), any(), any())).thenReturn(Map.of(SALA, false, "ana@example.test", false));
        var resultado = service.consultar(request(null), null, "token");
        assertThat(resultado.salas().getFirst().disponivel()).isFalse();
        assertThat(resultado.participantes().getFirst().disponivel()).isFalse();
    }

    @Test void consultaNaoVerificadaNaoSignificaDisponivel() {
        preparar(List.of());
        when(google.consultar(any(), any(), any(), any(), any())).thenReturn(Map.of());
        assertThat(service.consultar(request(null), null, "token").salas().getFirst().disponivel()).isNull();
        var nova = reuniao("10:00", "12:00"); nova.setIdReuniao(null);
        assertThatThrownBy(() -> service.exigirLocalDisponivel(nova, "token"))
                .isInstanceOf(ResponseStatusException.class).hasMessageContaining("verificar");
    }

    @Test void rejeitaReservaDeSalaOuLocalOcupadoAoSalvar() {
        preparar(List.of(reuniao("11:00", "12:00")));
        var nova = reuniao("10:00", "12:00"); nova.setIdReuniao(null);
        assertThatThrownBy(() -> service.exigirLocalDisponivel(nova, "token"))
                .isInstanceOf(ResponseStatusException.class).hasMessageContaining("409");
        nova.setSalaEmail(null);
        assertThatThrownBy(() -> service.exigirLocalDisponivel(nova, "token"))
                .isInstanceOf(ResponseStatusException.class).hasMessageContaining("409");
    }

    @Test void preservaDuracaoAntigaDeUmaHoraERejeitaTerminoInvalido() {
        var antiga = reuniao("10:00", "12:00"); antiga.setDataHoraFim(null);
        assertThat(antiga.getDataHoraFim()).isEqualTo(DATA.atTime(11, 0));
        assertThatThrownBy(() -> ReuniaoDisponibilidadeService.fim(DATA, LocalTime.NOON, LocalTime.of(11, 0)))
                .isInstanceOf(ResponseStatusException.class).hasMessageContaining("posterior");
    }

    private void preparar(List<Reuniao> existentes) {
        when(reunioes.findByDataBetween(any(), any())).thenReturn(existentes);
        lenient().when(usuarios.findAllById(any())).thenReturn(List.of(usuario()));
        when(google.consultar(any(), any(), any(), any(), any())).thenReturn(Map.of(SALA, true, "ana@example.test", true));
    }

    private ReuniaoDisponibilidadeRequestDTO request(Long id) {
        return new ReuniaoDisponibilidadeRequestDTO(DATA, LocalTime.of(10, 0), LocalTime.NOON, id,
                List.of(SALA), List.of(2L), "Sala 1");
    }

    private Reuniao reuniao(String inicio, String fim) {
        var reuniao = new Reuniao(); reuniao.setIdReuniao(3L); reuniao.setData(DATA); reuniao.setHora(LocalTime.parse(inicio));
        reuniao.setDataHoraFim(DATA.atTime(LocalTime.parse(fim))); reuniao.setPresencial(true);
        reuniao.setSalaEmail(SALA); reuniao.setLocal("Sala 1"); return reuniao;
    }

    private Usuario usuario() {
        var usuario = new Usuario(); usuario.setId(2L); usuario.setEmail("ana@example.test"); return usuario;
    }
}
