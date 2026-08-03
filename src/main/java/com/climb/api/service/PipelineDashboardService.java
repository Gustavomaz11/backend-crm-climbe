package com.climb.api.service;

import com.climb.api.model.PipelineVendasNegocio;
import com.climb.api.model.PipelineVendasMovimentacaoEtapa;
import com.climb.api.model.PermissaoCodigo;
import com.climb.api.model.dto.PipelineDashboardFiltroDTO;
import com.climb.api.model.dto.PipelineDashboardResponseDTO;
import com.climb.api.model.enums.PipelineTarefaStatus;
import com.climb.api.repository.PipelineVendasMovimentacaoEtapaRepository;
import com.climb.api.repository.PipelineVendasNegocioRepository;
import com.climb.api.repository.PipelineVendasTarefaRepository;
import com.climb.api.repository.PipelineFiltroOptionProjection;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.TreeSet;

@Service
public class PipelineDashboardService {
    private final PipelineVendasNegocioRepository negocioRepository;
    private final PipelineVendasMovimentacaoEtapaRepository movimentacaoRepository;
    private final PipelineVendasTarefaRepository tarefaRepository;
    private final PipelineDashboardCalculator calculator;
    private final RbacService rbacService;

    public PipelineDashboardService(PipelineVendasNegocioRepository negocioRepository,
                                    PipelineVendasMovimentacaoEtapaRepository movimentacaoRepository,
                                    PipelineVendasTarefaRepository tarefaRepository,
                                    PipelineDashboardCalculator calculator,
                                    RbacService rbacService) {
        this.negocioRepository = negocioRepository;
        this.movimentacaoRepository = movimentacaoRepository;
        this.tarefaRepository = tarefaRepository;
        this.calculator = calculator;
        this.rbacService = rbacService;
    }

    @Transactional(readOnly = true)
    public PipelineDashboardResponseDTO buscar(Long usuarioId, PipelineDashboardFiltroDTO filtro) {
        exigirPermissao(usuarioId);
        validarPeriodo(filtro);
        List<PipelineVendasNegocio> negocios = negocioRepository.findDashboardNegocios(
                inicio(filtro), fimExclusivo(filtro), filtro.responsavelId(), filtro.funilId(), filtro.empresaId(),
                filtro.situacao(), normalizar(filtro.estrategia()), normalizar(filtro.servico()),
                normalizar(filtro.origem()));
        List<Long> ids = negocios.stream().map(PipelineVendasNegocio::getIdNegocio).toList();
        List<PipelineVendasMovimentacaoEtapa> movimentacoes = ids.isEmpty()
                ? List.of()
                : movimentacaoRepository.findByNegocioIdNegocioIn(ids);
        long tarefasAtrasadas = contarTarefasAtrasadas(ids);
        return calculator.calcular(negocios, movimentacoes, tarefasAtrasadas, opcoes());
    }

    private LocalDateTime inicio(PipelineDashboardFiltroDTO filtro) {
        return filtro.dataInicio() == null ? null : filtro.dataInicio().atStartOfDay();
    }

    private LocalDateTime fimExclusivo(PipelineDashboardFiltroDTO filtro) {
        return filtro.dataFim() == null ? null : filtro.dataFim().plusDays(1).atStartOfDay();
    }

    private long contarTarefasAtrasadas(List<Long> negocioIds) {
        if (negocioIds.isEmpty()) return 0;
        return tarefaRepository.countByNegocioIdNegocioInAndPrazoBeforeAndStatusNotIn(
                negocioIds,
                LocalDate.now(),
                List.of(PipelineTarefaStatus.CONCLUIDA, PipelineTarefaStatus.CANCELADA));
    }

    private PipelineDashboardResponseDTO.OpcoesFiltro opcoes() {
        TreeSet<String> estrategias = new TreeSet<>();
        TreeSet<String> servicos = new TreeSet<>();
        TreeSet<String> origens = new TreeSet<>();
        for (PipelineFiltroOptionProjection opcao : negocioRepository.findDashboardFilterOptions()) {
            adicionarOpcao(estrategias, opcao.getEstrategia());
            adicionarOpcao(servicos, opcao.getServico());
            adicionarOpcao(origens, opcao.getOrigem());
        }
        return new PipelineDashboardResponseDTO.OpcoesFiltro(
                List.copyOf(estrategias), List.copyOf(servicos), List.copyOf(origens)
        );
    }

    private void adicionarOpcao(TreeSet<String> opcoes, String valor) {
        if (valor != null && !valor.isBlank()) opcoes.add(valor.trim());
    }

    private String normalizar(String valor) {
        return valor == null || valor.isBlank() ? null : valor.trim();
    }

    private void validarPeriodo(PipelineDashboardFiltroDTO filtro) {
        if (filtro.dataInicio() != null && filtro.dataFim() != null
                && filtro.dataInicio().isAfter(filtro.dataFim())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A data inicial deve ser anterior à data final");
        }
    }

    private void exigirPermissao(Long usuarioId) {
        if (usuarioId == null || !rbacService.temPermissao(usuarioId, PermissaoCodigo.COMERCIAL_DASHBOARD_VISUALIZAR)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Usuário sem permissão para visualizar o dashboard comercial");
        }
    }
}
