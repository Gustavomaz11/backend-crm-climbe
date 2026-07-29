package com.climb.api.service;

import com.climb.api.model.PipelineVendasNegocio;
import com.climb.api.model.PipelineVendasMovimentacaoEtapa;
import com.climb.api.model.PipelineVendasTarefa;
import com.climb.api.model.PermissaoCodigo;
import com.climb.api.model.dto.PipelineDashboardFiltroDTO;
import com.climb.api.model.dto.PipelineDashboardResponseDTO;
import com.climb.api.model.enums.PipelineTarefaStatus;
import com.climb.api.repository.PipelineVendasMovimentacaoEtapaRepository;
import com.climb.api.repository.PipelineVendasNegocioRepository;
import com.climb.api.repository.PipelineVendasTarefaRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

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
        List<PipelineVendasNegocio> todos = negocioRepository.findAllByOrderByCriadoEmDesc();
        List<PipelineVendasNegocio> negocios = todos.stream()
                .filter(negocio -> corresponde(negocio, filtro))
                .toList();
        List<Long> ids = negocios.stream().map(PipelineVendasNegocio::getIdNegocio).toList();
        List<PipelineVendasMovimentacaoEtapa> movimentacoes = ids.isEmpty()
                ? List.of()
                : movimentacaoRepository.findByNegocioIdNegocioIn(ids);
        long tarefasAtrasadas = contarTarefasAtrasadas(ids);
        return calculator.calcular(negocios, movimentacoes, tarefasAtrasadas, opcoes(todos));
    }

    private boolean corresponde(PipelineVendasNegocio negocio, PipelineDashboardFiltroDTO filtro) {
        LocalDate criacao = negocio.getCriadoEm().toLocalDate();
        if (filtro.dataInicio() != null && criacao.isBefore(filtro.dataInicio())) return false;
        if (filtro.dataFim() != null && criacao.isAfter(filtro.dataFim())) return false;
        if (filtro.responsavelId() != null && !Objects.equals(filtro.responsavelId(), negocio.getResponsavel().getId())) return false;
        if (filtro.funilId() != null && !Objects.equals(filtro.funilId(), negocio.getFunil().getIdFunil())) return false;
        if (filtro.empresaId() != null && (negocio.getEmpresa() == null
                || !Objects.equals(filtro.empresaId(), negocio.getEmpresa().getIdEmpresa()))) return false;
        if (filtro.situacao() != null && filtro.situacao() != negocio.getResultado()) return false;
        return textoIgual(filtro.estrategia(), negocio.getEstrategiaComercial())
                && textoIgual(filtro.servico(), negocio.getServicoInteresse())
                && textoIgual(filtro.origem(), negocio.getOrigemNegocio());
    }

    private long contarTarefasAtrasadas(List<Long> negocioIds) {
        if (negocioIds.isEmpty()) return 0;
        LocalDate hoje = LocalDate.now();
        return tarefaRepository.findAllByOrderByPrazoAscCriadoEmDesc().stream()
                .filter(tarefa -> negocioIds.contains(tarefa.getNegocio().getIdNegocio()))
                .filter(tarefa -> tarefa.getPrazo() != null && tarefa.getPrazo().isBefore(hoje))
                .filter(this::naoFinalizada)
                .count();
    }

    private boolean naoFinalizada(PipelineVendasTarefa tarefa) {
        return tarefa.getStatus() != PipelineTarefaStatus.CONCLUIDA
                && tarefa.getStatus() != PipelineTarefaStatus.CANCELADA;
    }

    private PipelineDashboardResponseDTO.OpcoesFiltro opcoes(List<PipelineVendasNegocio> negocios) {
        return new PipelineDashboardResponseDTO.OpcoesFiltro(
                valores(negocios, PipelineVendasNegocio::getEstrategiaComercial),
                valores(negocios, PipelineVendasNegocio::getServicoInteresse),
                valores(negocios, PipelineVendasNegocio::getOrigemNegocio)
        );
    }

    private List<String> valores(List<PipelineVendasNegocio> negocios,
                                 Function<PipelineVendasNegocio, String> extrator) {
        return negocios.stream().map(extrator).filter(Objects::nonNull).map(String::trim)
                .filter(valor -> !valor.isBlank()).distinct().sorted(Comparator.naturalOrder()).toList();
    }

    private boolean textoIgual(String filtro, String valor) {
        return filtro == null || filtro.isBlank() || (valor != null && filtro.trim().equalsIgnoreCase(valor.trim()));
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
