package com.climb.api.service;

import com.climb.api.model.PermissaoCodigo;
import com.climb.api.model.PipelineVendasNegocio;
import com.climb.api.model.dto.PipelineDashboardFiltroDTO;
import com.climb.api.model.dto.PipelineDashboardResponseDTO;
import com.climb.api.model.enums.PipelineTarefaStatus;
import com.climb.api.model.enums.PipelineVendasResultado;
import com.climb.api.repository.PipelineVendasMovimentacaoEtapaRepository;
import com.climb.api.repository.PipelineVendasNegocioRepository;
import com.climb.api.repository.PipelineVendasTarefaRepository;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PipelineDashboardServiceTest {

    @Test
    void deveFiltrarEContarNoBancoSemCarregarTodasAsTarefas() {
        PipelineVendasNegocioRepository negocioRepository = mock(PipelineVendasNegocioRepository.class);
        PipelineVendasMovimentacaoEtapaRepository movimentacaoRepository =
                mock(PipelineVendasMovimentacaoEtapaRepository.class);
        PipelineVendasTarefaRepository tarefaRepository = mock(PipelineVendasTarefaRepository.class);
        PipelineDashboardCalculator calculator = mock(PipelineDashboardCalculator.class);
        RbacService rbacService = mock(RbacService.class);
        PipelineDashboardService service = new PipelineDashboardService(
                negocioRepository, movimentacaoRepository, tarefaRepository, calculator, rbacService);
        PipelineDashboardFiltroDTO filtro = new PipelineDashboardFiltroDTO(
                LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 31), 4L, 2L,
                " Ativa ", " BPO ", " Site ", 8L, PipelineVendasResultado.ABERTO);
        PipelineVendasNegocio negocio = new PipelineVendasNegocio();
        negocio.setIdNegocio(10L);
        PipelineDashboardResponseDTO esperado = new PipelineDashboardResponseDTO(
                null, null, null, null, null, null, null, null, null);

        when(rbacService.temPermissao(3L, PermissaoCodigo.COMERCIAL_DASHBOARD_VISUALIZAR)).thenReturn(true);
        when(negocioRepository.findDashboardNegocios(any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(List.of(negocio));
        when(movimentacaoRepository.findByNegocioIdNegocioIn(List.of(10L))).thenReturn(List.of());
        when(tarefaRepository.countByNegocioIdNegocioInAndPrazoBeforeAndStatusNotIn(
                anyList(), any(), anyList())).thenReturn(3L);
        when(negocioRepository.findDashboardFilterOptions()).thenReturn(List.of());
        when(calculator.calcular(anyList(), anyList(), any(Long.class), any())).thenReturn(esperado);

        PipelineDashboardResponseDTO resultado = service.buscar(3L, filtro);

        assertSame(esperado, resultado);
        verify(negocioRepository).findDashboardNegocios(
                LocalDate.of(2026, 7, 1).atStartOfDay(),
                LocalDate.of(2026, 8, 1).atStartOfDay(),
                4L, 2L, 8L, PipelineVendasResultado.ABERTO, "Ativa", "BPO", "Site");
        verify(tarefaRepository).countByNegocioIdNegocioInAndPrazoBeforeAndStatusNotIn(
                List.of(10L), LocalDate.now(),
                List.of(PipelineTarefaStatus.CONCLUIDA, PipelineTarefaStatus.CANCELADA));
    }
}
