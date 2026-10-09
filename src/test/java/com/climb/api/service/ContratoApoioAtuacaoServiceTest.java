package com.climb.api.service;

import com.climb.api.model.*;
import com.climb.api.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.time.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ContratoApoioAtuacaoServiceTest {
    private final ContratoApoioAtuacaoRepository atuacoes = mock(ContratoApoioAtuacaoRepository.class);
    private final ContratoRepository contratos = mock(ContratoRepository.class);
    private final List<ContratoApoioAtuacao> periodos = new ArrayList<>();
    private ContratoKanbanTask tarefa;
    private Usuario apoio;

    @BeforeEach void setup() {
        var contrato = new Contrato(); contrato.setIdContrato(10L);
        tarefa = new ContratoKanbanTask(); tarefa.setIdTask(20L); tarefa.setContrato(contrato);
        tarefa.setCriadoEm(LocalDateTime.of(2026, 10, 1, 9, 0)); tarefa.setDataInicio(LocalDate.of(2027, 1, 1));
        tarefa.setRaia(new ContratoKanbanRaia()); apoio = new Usuario(); apoio.setId(3L);
        tarefa.setResponsaveis(List.of(apoio)); tarefa.getApoios().add(apoio);
        when(contratos.findByIdForUpdate(10L)).thenReturn(Optional.of(contrato));
        when(atuacoes.findByTarefa_IdTaskAndFimIsNull(20L)).thenAnswer(i -> periodos.stream()
                .filter(p -> p.getTarefa() != null && p.getFim() == null).toList());
        when(atuacoes.findByTarefa_IdTask(20L)).thenAnswer(i -> periodos.stream().filter(p -> p.getTarefa() != null).toList());
        when(atuacoes.save(any())).thenAnswer(i -> { ContratoApoioAtuacao p = i.getArgument(0); if (!periodos.contains(p)) periodos.add(p); return p; });
    }

    @Test void registraADataDaAtribuicaoEFechaAoConcluirSemDuplicarAoSalvar() {
        var novembro = em("2026-11-15T12:00:00Z"); novembro.sincronizar(tarefa); novembro.sincronizar(tarefa);
        assertThat(periodos).hasSize(1);
        assertThat(periodos.getFirst().getInicio()).isEqualTo(LocalDateTime.of(2026, 11, 15, 9, 0));
        assertThat(periodos.getFirst().getFim()).isNull();
        tarefa.getRaia().setConcluiTarefas(true); em("2026-12-08T12:00:00Z").sincronizar(tarefa);
        assertThat(periodos.getFirst().getFim()).isEqualTo(LocalDateTime.of(2026, 12, 8, 9, 0));
    }

    @Test void reabrirOuReatribuirCriaOutroPeriodoEConservaOHistorico() {
        em("2026-11-15T12:00:00Z").sincronizar(tarefa);
        tarefa.setResponsaveis(List.of()); tarefa.getApoios().clear(); em("2026-11-20T12:00:00Z").sincronizar(tarefa);
        tarefa.setResponsaveis(List.of(apoio)); tarefa.getApoios().add(apoio); em("2026-12-02T12:00:00Z").sincronizar(tarefa);
        assertThat(periodos).hasSize(2);
        assertThat(periodos.getFirst().getFim()).isEqualTo(LocalDateTime.of(2026, 11, 20, 9, 0));
        assertThat(periodos.get(1).getInicio()).isEqualTo(LocalDateTime.of(2026, 12, 2, 9, 0));
        assertThat(periodos.get(1).getFim()).isNull();
    }

    @Test void excluirTarefaEncerraAtuacaoPreservaHistoricoEPermiteRemoverReferencia() {
        em("2026-11-15T12:00:00Z").sincronizar(tarefa);
        var dezembro = em("2026-12-08T12:00:00Z"); dezembro.encerrar(tarefa); dezembro.desvincular(tarefa);
        assertThat(periodos).hasSize(1);
        assertThat(periodos.getFirst().getTarefa()).isNull();
        assertThat(periodos.getFirst().getFim()).isEqualTo(LocalDateTime.of(2026, 12, 8, 9, 0));
        verify(atuacoes).flush();
    }

    @Test void pessoaSemVinculoDeApoioETarefaJaConcluidaNaoAbremPeriodo() {
        tarefa.getApoios().clear(); em("2026-11-15T12:00:00Z").sincronizar(tarefa);
        tarefa.getApoios().add(apoio); tarefa.getRaia().setConcluiTarefas(true); em("2026-11-15T12:00:00Z").sincronizar(tarefa);
        assertThat(periodos).isEmpty();
    }
    private ContratoApoioAtuacaoService em(String instante) {
        return new ContratoApoioAtuacaoService(atuacoes, contratos, Clock.fixed(Instant.parse(instante), ZoneId.of("America/Sao_Paulo")));
    }
}
